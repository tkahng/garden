package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record DepartmentRequest(
    @NotBlank String name,
    UUID parentId
) {}
