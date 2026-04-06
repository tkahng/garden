package io.k2dv.garden.admin.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(@NotBlank String name, String description) {}
