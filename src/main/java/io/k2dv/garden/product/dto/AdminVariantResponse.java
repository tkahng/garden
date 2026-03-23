package io.k2dv.garden.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminVariantResponse(
    UUID id,
    String title,
    String sku,
    String barcode,
    BigDecimal price,
    BigDecimal compareAtPrice,
    BigDecimal weight,
    String weightUnit,
    List<OptionValueLabel> optionValues,
    Instant deletedAt
) {}
