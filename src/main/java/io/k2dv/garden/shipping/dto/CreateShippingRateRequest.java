package io.k2dv.garden.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateShippingRateRequest(
    @NotBlank String name,
    @NotNull @PositiveOrZero BigDecimal price,
    Integer minWeightGrams,
    Integer maxWeightGrams,
    BigDecimal minOrderAmount,
    Integer estimatedDaysMin,
    Integer estimatedDaysMax,
    String carrier
) {}
