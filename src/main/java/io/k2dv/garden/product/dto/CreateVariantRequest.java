package io.k2dv.garden.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateVariantRequest(
    BigDecimal price,
    BigDecimal compareAtPrice,
    String sku,
    String barcode,
    BigDecimal weight,
    String weightUnit,
    List<UUID> optionValueIds
) {}
