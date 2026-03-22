package io.k2dv.garden.admin.iam.dto;

import io.k2dv.garden.iam.model.Permission;

import java.util.UUID;

public record PermissionResponse(UUID id, String name, String resource, String action) {
    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(p.getId(), p.getName(), p.getResource(), p.getAction());
    }
}
