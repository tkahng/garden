package io.k2dv.garden.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLocationRequest(
    @NotBlank String name,
    String address
) {}
