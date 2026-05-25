package io.k2dv.garden.cart.dto;

import java.util.List;
import java.util.UUID;

public record BulkAddToCartResponse(
    CartResponse cart,
    List<LineResult> results
) {
    public record LineResult(
        String sku,
        int quantity,
        Status status,
        UUID variantId,
        String productTitle,
        String message
    ) {}

    public enum Status { ADDED, NOT_FOUND, ERROR }
}
