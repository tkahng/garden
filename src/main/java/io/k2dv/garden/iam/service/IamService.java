package io.k2dv.garden.iam.service;

import io.k2dv.garden.iam.model.Role;
import io.k2dv.garden.iam.repository.PermissionRepository;
import io.k2dv.garden.iam.repository.RoleRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IamService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;

    @Transactional(readOnly = true)
    public List<String> loadPermissionsForUser(UUID userId) {
        List<String> roleNames = userRepo.findRoleNamesByUserId(userId);
        if (roleNames.contains("OWNER")) {
            return permissionRepo.findAllNames();
        }
        return userRepo.findPermissionNamesByUserId(userId);
    }

    @Transactional
    public void assignRoleByName(UUID userId, String roleName) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        Role role = roleRepo.findByName(roleName)
            .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Role not found: " + roleName));
        user.getRoles().add(role);
        userRepo.save(user);
    }

    @Transactional
    public void removeRoleByName(UUID userId, String roleName) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        user.getRoles().removeIf(r -> r.getName().equals(roleName));
        userRepo.save(user);
    }
}
