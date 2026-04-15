package io.k2dv.garden.shipping.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateShippingZoneRequest(
    @NotBlank String name,
    String description,
    List<String> countryCodes,
    List<String> provinces
) {}
