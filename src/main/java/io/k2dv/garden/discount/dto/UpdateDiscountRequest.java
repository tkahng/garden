package io.k2dv.garden.discount.dto;

import io.k2dv.garden.discount.model.DiscountType;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateDiscountRequest(
    String code,
    DiscountType type,
    @PositiveOrZero BigDecimal value,
    BigDecimal minOrderAmount,
    Integer maxUses,
    Instant startsAt,
    Instant endsAt,
    Boolean isActive
) {}
