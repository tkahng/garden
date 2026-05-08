package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.PriceListAdjustmentType;

import java.math.BigDecimal;
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
    PriceListAdjustmentType adjustmentType,
    BigDecimal adjustmentValue,
    Instant createdAt,
    Instant updatedAt
) {}
