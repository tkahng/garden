package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record UpdatePriceListRequest(
    @NotBlank String name,
    String currency,
    Integer priority,
    Instant startsAt,
    Instant endsAt
) {}
