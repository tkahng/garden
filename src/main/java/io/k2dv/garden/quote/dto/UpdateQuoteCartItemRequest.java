package io.k2dv.garden.quote.dto;

import jakarta.validation.constraints.Min;

public record UpdateQuoteCartItemRequest(
    @Min(1) int quantity,
    String note
) {}
