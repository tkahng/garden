package io.k2dv.garden.shipping.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateShippingRateRequest(
    String name,
    @PositiveOrZero BigDecimal price,
    Integer minWeightGrams,
    Integer maxWeightGrams,
    BigDecimal minOrderAmount,
    Integer estimatedDaysMin,
    Integer estimatedDaysMax,
    String carrier,
    Boolean isActive
) {}
