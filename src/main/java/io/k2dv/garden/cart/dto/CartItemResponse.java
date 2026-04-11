package io.k2dv.garden.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
    UUID id,
    UUID variantId,
    int quantity,
    BigDecimal unitPrice,
    CartItemProductInfo product
) {}
