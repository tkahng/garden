package io.k2dv.garden.quote.dto;

import io.k2dv.garden.cart.dto.CartItemProductInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QuoteCartItemResponse(
    UUID id,
    UUID variantId,
    int quantity,
    String note,
    CartItemProductInfo product,
    BigDecimal estimatedUnitPrice,
    Instant createdAt
) {}
