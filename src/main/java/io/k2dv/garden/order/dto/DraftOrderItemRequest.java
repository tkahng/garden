package io.k2dv.garden.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DraftOrderItemRequest(
    @NotNull UUID variantId,
    @Min(1) int quantity,
    BigDecimal unitPrice
) {}
