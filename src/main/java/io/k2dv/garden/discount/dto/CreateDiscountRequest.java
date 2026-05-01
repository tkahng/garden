package io.k2dv.garden.discount.dto;

import io.k2dv.garden.discount.model.DiscountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateDiscountRequest(
    String code,
    boolean automatic,
    @NotNull DiscountType type,
    @NotNull @PositiveOrZero BigDecimal value,
    BigDecimal minOrderAmount,
    Integer maxUses,
    Instant startsAt,
    Instant endsAt,
    UUID companyId
) {}
