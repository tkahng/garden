package io.k2dv.garden.admin.iam.dto;

import io.k2dv.garden.iam.model.Role;

import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, String description, List<PermissionResponse> permissions) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(
            role.getId(),
            role.getName(),
            role.getDescription(),
            role.getPermissions().stream().map(PermissionResponse::from).toList()
        );
    }
}
