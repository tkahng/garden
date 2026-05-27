package io.k2dv.garden.admin.iam.service;

import io.k2dv.garden.admin.iam.dto.*;
import io.k2dv.garden.iam.model.Role;
import io.k2dv.garden.iam.repository.PermissionRepository;
import io.k2dv.garden.iam.repository.RoleRepository;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Admin service for managing the role and permission catalog used by the IAM system.
 * Allows creation of custom roles, assignment of fine-grained permissions to roles,
 * and deletion of non-system roles. Predefined system roles (CUSTOMER, STAFF, MANAGER, OWNER)
 * are protected from deletion.
 */
@Service
@RequiredArgsConstructor
public class AdminIamService {

    private static final Set<String> PREDEFINED_ROLES = Set.of("CUSTOMER", "STAFF", "MANAGER", "OWNER");

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;

    /**
     * Returns all roles defined in the system, including both predefined and custom roles,
     * with their associated permissions.
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepo.findAll().stream().map(RoleResponse::from).toList();
    }

    /**
     * Creates a new custom role with a unique name and optional description.
     * Role names are case-sensitive and must be unique across the system.
     */
    @Transactional
    public RoleResponse createRole(CreateRoleRequest req) {
        if (roleRepo.findByName(req.name()).isPresent()) {
            throw new ConflictException("ROLE_NAME_TAKEN", "A role with this name already exists");
        }
        Role role = new Role();
        role.setName(req.name());
        role.setDescription(req.description());
        return RoleResponse.from(roleRepo.save(role));
    }

    /**
     * Updates the name and/or description of an existing role; only non-null fields are applied.
     */
    @Transactional
    public RoleResponse updateRole(UUID id, UpdateRoleRequest req) {
        Role role = findRole(id);
        if (req.name() != null) role.setName(req.name());
        if (req.description() != null) role.setDescription(req.description());
        return RoleResponse.from(roleRepo.save(role));
    }

    /**
     * Deletes a custom role. Predefined system roles (CUSTOMER, STAFF, MANAGER, OWNER)
     * are immutable and cannot be removed.
     */
    @Transactional
    public void deleteRole(UUID id) {
        Role role = findRole(id);
        if (PREDEFINED_ROLES.contains(role.getName())) {
            throw new ForbiddenException("PREDEFINED_ROLE", "Predefined roles cannot be deleted");
        }
        roleRepo.delete(role);
    }

    /**
     * Returns all permissions registered in the system, used to populate the permission
     * picker when configuring a role.
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepo.findAll().stream().map(PermissionResponse::from).toList();
    }

    /**
     * Adds a permission to a role's permission set; idempotent if already assigned.
     * The change is reflected immediately for future token mints but does not evict
     * existing user permission caches — a user re-login is required to pick up the change.
     */
    @Transactional
    public RoleResponse assignPermission(UUID roleId, AssignPermissionRequest req) {
        Role role = findRole(roleId);
        var permission = permissionRepo.findById(req.permissionId())
            .orElseThrow(() -> new NotFoundException("PERMISSION_NOT_FOUND", "Permission not found"));
        role.getPermissions().add(permission);
        return RoleResponse.from(roleRepo.save(role));
    }

    /**
     * Removes a permission from a role; no-ops if the permission was not assigned.
     */
    @Transactional
    public void removePermission(UUID roleId, UUID permissionId) {
        Role role = findRole(roleId);
        role.getPermissions().removeIf(p -> p.getId().equals(permissionId));
        roleRepo.save(role);
    }

    private Role findRole(UUID id) {
        return roleRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Role not found"));
    }
}
