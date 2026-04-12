package io.k2dv.garden.quote.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateQuoteItemRequest(
    @Min(1) int quantity,
    @NotNull BigDecimal unitPrice
) {}
