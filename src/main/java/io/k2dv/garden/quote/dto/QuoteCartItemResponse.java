package io.k2dv.garden.quote.dto;

import java.time.Instant;
import java.util.UUID;

public record QuoteCartItemResponse(
    UUID id,
    UUID variantId,
    int quantity,
    String note,
    Instant createdAt
) {}
