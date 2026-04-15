package io.k2dv.garden.shipping.dto;

import java.util.List;

public record UpdateShippingZoneRequest(
    String name,
    String description,
    List<String> countryCodes,
    List<String> provinces,
    Boolean isActive
) {}
