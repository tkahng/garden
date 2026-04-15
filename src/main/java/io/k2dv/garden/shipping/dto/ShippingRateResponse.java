package io.k2dv.garden.shipping.dto;

import io.k2dv.garden.shipping.model.ShippingRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShippingRateResponse(
    UUID id,
    UUID zoneId,
    String name,
    BigDecimal price,
    Integer minWeightGrams,
    Integer maxWeightGrams,
    BigDecimal minOrderAmount,
    Integer estimatedDaysMin,
    Integer estimatedDaysMax,
    String carrier,
    boolean isActive,
    Instant createdAt
) {
    public static ShippingRateResponse from(ShippingRate r) {
        return new ShippingRateResponse(
            r.getId(), r.getZoneId(), r.getName(), r.getPrice(),
            r.getMinWeightGrams(), r.getMaxWeightGrams(), r.getMinOrderAmount(),
            r.getEstimatedDaysMin(), r.getEstimatedDaysMax(),
            r.getCarrier(), r.isActive(), r.getCreatedAt()
        );
    }
}
