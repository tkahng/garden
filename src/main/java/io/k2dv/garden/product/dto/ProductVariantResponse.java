package io.k2dv.garden.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductVariantResponse(
    UUID id,
    String title,
    String sku,
    BigDecimal price,
    BigDecimal compareAtPrice,
    List<OptionValueLabel> optionValues
) {}
