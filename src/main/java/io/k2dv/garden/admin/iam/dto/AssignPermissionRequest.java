package io.k2dv.garden.admin.iam.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignPermissionRequest(@NotNull UUID permissionId) {}
