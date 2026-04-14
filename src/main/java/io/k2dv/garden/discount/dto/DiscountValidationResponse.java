package io.k2dv.garden.discount.dto;

import io.k2dv.garden.discount.model.DiscountType;

import java.math.BigDecimal;

public record DiscountValidationResponse(
    boolean valid,
    String code,
    DiscountType type,
    BigDecimal value,
    BigDecimal discountedAmount,
    String message
) {}
