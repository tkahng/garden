package io.k2dv.garden.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VariantLookupResponse(
    UUID variantId,
    UUID productId,
    String productTitle,
    String productHandle,
    String variantTitle,
    String sku,
    BigDecimal price,
    String featuredImageUrl
) {}
