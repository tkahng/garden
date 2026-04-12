package io.k2dv.garden.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QuoteItemResponse(
    UUID id,
    UUID variantId,
    String description,
    int quantity,
    BigDecimal unitPrice,
    Instant createdAt
) {}
