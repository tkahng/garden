package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameOptionValueRequest(@NotBlank String label) {}
