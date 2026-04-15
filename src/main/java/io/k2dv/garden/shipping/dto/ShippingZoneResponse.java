package io.k2dv.garden.shipping.dto;

import io.k2dv.garden.shipping.model.ShippingZone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShippingZoneResponse(
    UUID id,
    String name,
    String description,
    List<String> countryCodes,
    List<String> provinces,
    boolean isActive,
    Instant createdAt
) {
    public static ShippingZoneResponse from(ShippingZone z) {
        return new ShippingZoneResponse(
            z.getId(), z.getName(), z.getDescription(),
            z.getCountryCodes(), z.getProvinces(),
            z.isActive(), z.getCreatedAt()
        );
    }
}
