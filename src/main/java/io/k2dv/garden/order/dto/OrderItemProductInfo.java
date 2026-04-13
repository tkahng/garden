package io.k2dv.garden.order.dto;

import java.util.UUID;

public record OrderItemProductInfo(
    UUID productId,
    String productTitle,
    String variantTitle,
    String imageUrl
) {}
