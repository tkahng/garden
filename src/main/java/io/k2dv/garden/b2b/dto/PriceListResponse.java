package io.k2dv.garden.b2b.dto;

import java.time.Instant;
import java.util.UUID;

public record PriceListResponse(
    UUID id,
    UUID companyId,
    String name,
    String currency,
    int priority,
    Instant startsAt,
    Instant endsAt,
    Instant createdAt,
    Instant updatedAt
) {}
