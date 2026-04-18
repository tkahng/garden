package io.k2dv.garden.b2b.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PriceListEntryResponse(
    UUID id,
    UUID priceListId,
    UUID variantId,
    BigDecimal price,
    int minQty,
    Instant createdAt,
    Instant updatedAt
) {}
