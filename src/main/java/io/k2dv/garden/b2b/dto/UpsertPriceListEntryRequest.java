package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpsertPriceListEntryRequest(
    @NotNull @PositiveOrZero BigDecimal price,
    @Min(1) int minQty
) {
    public UpsertPriceListEntryRequest {
        if (minQty < 1) minQty = 1;
    }
}
