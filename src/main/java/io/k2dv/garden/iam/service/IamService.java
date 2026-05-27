package io.k2dv.garden.iam.service;

import io.k2dv.garden.iam.model.Role;
import io.k2dv.garden.iam.repository.PermissionRepository;
import io.k2dv.garden.iam.repository.RoleRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Core IAM service responsible for resolving a user's effective permission set and
 * managing role assignments. The {@link #loadPermissionsForUser} result is cached per user
 * and embedded in JWT claims at token-mint time; cache entries are evicted whenever a
 * role is assigned or removed so the next token always reflects the current state.
 */
@Service
@RequiredArgsConstructor
public class IamService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;

    /**
     * Returns the full list of permission strings (e.g. "order:read", "product:write")
     * that are effective for this user. Users with the OWNER role receive every permission
     * in the system without needing explicit role assignments.
     * Results are cached under the "permissions" cache keyed by userId.
     */
    @Cacheable(value = "permissions", key = "#userId")
    @Transactional(readOnly = true)
    public List<String> loadPermissionsForUser(UUID userId) {
        List<String> roleNames = userRepo.findRoleNamesByUserId(userId);
        if (roleNames.contains("OWNER")) {
            return permissionRepo.findAllNames();
        }
        return userRepo.findPermissionNamesByUserId(userId);
    }

    /**
     * Grants a named role to a user and evicts their cached permissions so the change
     * takes effect on the next token refresh.
     */
    @CacheEvict(value = "permissions", key = "#userId")
    @Transactional
    public void assignRoleByName(UUID userId, String roleName) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        Role role = roleRepo.findByName(roleName)
            .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Role not found: " + roleName));
        user.getRoles().add(role);
        userRepo.save(user);
    }

    /**
     * Revokes a named role from a user and evicts their cached permissions so the
     * reduced permission set is reflected on the next token refresh.
     */
    @CacheEvict(value = "permissions", key = "#userId")
    @Transactional
    public void removeRoleByName(UUID userId, String roleName) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        user.getRoles().removeIf(r -> r.getName().equals(roleName));
        userRepo.save(user);
    }
}
