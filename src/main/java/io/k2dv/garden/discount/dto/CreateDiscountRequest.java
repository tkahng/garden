package io.k2dv.garden.discount.dto;

import io.k2dv.garden.discount.model.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateDiscountRequest(
    @NotBlank String code,
    @NotNull DiscountType type,
    @NotNull @PositiveOrZero BigDecimal value,
    BigDecimal minOrderAmount,
    Integer maxUses,
    Instant startsAt,
    Instant endsAt
) {}
