package io.k2dv.garden.order.template.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderTemplateItemInput(
    @NotNull UUID variantId,
    @Min(1) int quantity
) {}
