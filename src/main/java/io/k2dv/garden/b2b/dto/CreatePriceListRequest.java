package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreatePriceListRequest(
    @NotNull UUID companyId,
    @NotBlank String name,
    String currency,
    Integer priority,
    Instant startsAt,
    Instant endsAt
) {}
