package io.k2dv.garden.quote.dto;

import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateQuoteItemRequest(
    @Min(1) int quantity,
    BigDecimal unitPrice
) {}
