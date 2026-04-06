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

@Service
@RequiredArgsConstructor
public class AdminIamService {

    private static final Set<String> PREDEFINED_ROLES = Set.of("CUSTOMER", "STAFF", "MANAGER", "OWNER");

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepo.findAll().stream().map(RoleResponse::from).toList();
    }

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

    @Transactional
    public RoleResponse updateRole(UUID id, UpdateRoleRequest req) {
        Role role = findRole(id);
        if (req.name() != null) role.setName(req.name());
        if (req.description() != null) role.setDescription(req.description());
        return RoleResponse.from(roleRepo.save(role));
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = findRole(id);
        if (PREDEFINED_ROLES.contains(role.getName())) {
            throw new ForbiddenException("PREDEFINED_ROLE", "Predefined roles cannot be deleted");
        }
        roleRepo.delete(role);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepo.findAll().stream().map(PermissionResponse::from).toList();
    }

    @Transactional
    public RoleResponse assignPermission(UUID roleId, AssignPermissionRequest req) {
        Role role = findRole(roleId);
        var permission = permissionRepo.findById(req.permissionId())
            .orElseThrow(() -> new NotFoundException("PERMISSION_NOT_FOUND", "Permission not found"));
        role.getPermissions().add(permission);
        return RoleResponse.from(roleRepo.save(role));
    }

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
