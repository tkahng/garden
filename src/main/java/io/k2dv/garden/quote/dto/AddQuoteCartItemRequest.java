package io.k2dv.garden.quote.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddQuoteCartItemRequest(
    @NotNull UUID variantId,
    @Min(1) int quantity,
    String note
) {}
