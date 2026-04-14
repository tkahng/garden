package io.k2dv.garden.discount.dto;

import io.k2dv.garden.discount.model.DiscountType;

import java.math.BigDecimal;
import java.util.UUID;

public record DiscountApplication(
    UUID discountId,
    String code,
    DiscountType type,
    BigDecimal value,
    BigDecimal discountedAmount
) {}
