package io.k2dv.garden.quote.dto;

import io.k2dv.garden.quote.model.QuoteCartStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuoteCartResponse(
    UUID id,
    QuoteCartStatus status,
    List<QuoteCartItemResponse> items,
    Instant createdAt
) {}
