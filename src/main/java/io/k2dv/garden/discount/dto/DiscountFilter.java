package io.k2dv.garden.discount.dto;

import io.k2dv.garden.discount.model.DiscountType;

import java.util.UUID;

public record DiscountFilter(
    DiscountType type,
    Boolean isActive,
    String codeContains,
    UUID companyId
) {}
