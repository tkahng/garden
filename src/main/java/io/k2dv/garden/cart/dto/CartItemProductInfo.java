package io.k2dv.garden.cart.dto;

import java.util.UUID;

public record CartItemProductInfo(
    UUID productId,
    String productTitle,
    String variantTitle,
    String imageUrl
) {}
