package io.k2dv.garden.product.dto;

import java.math.BigDecimal;

public record UpdateVariantRequest(
    BigDecimal price,
    BigDecimal compareAtPrice,
    String sku,
    String barcode,
    BigDecimal weight,
    String weightUnit
) {}
