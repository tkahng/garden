package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.PriceListAdjustmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdatePriceListRequest(
    @NotBlank String name,
    String currency,
    Integer priority,
    Instant startsAt,
    Instant endsAt,
    PriceListAdjustmentType adjustmentType,
    @Positive BigDecimal adjustmentValue
) {}
