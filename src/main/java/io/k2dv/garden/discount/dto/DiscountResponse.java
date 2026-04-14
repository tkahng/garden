package io.k2dv.garden.discount.dto;

import io.k2dv.garden.discount.model.Discount;
import io.k2dv.garden.discount.model.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DiscountResponse(
    UUID id,
    String code,
    DiscountType type,
    BigDecimal value,
    BigDecimal minOrderAmount,
    Integer maxUses,
    int usedCount,
    Instant startsAt,
    Instant endsAt,
    boolean isActive,
    Instant createdAt
) {
    public static DiscountResponse from(Discount d) {
        return new DiscountResponse(
            d.getId(),
            d.getCode(),
            d.getType(),
            d.getValue(),
            d.getMinOrderAmount(),
            d.getMaxUses(),
            d.getUsedCount(),
            d.getStartsAt(),
            d.getEndsAt(),
            d.isActive(),
            d.getCreatedAt()
        );
    }
}
