package io.k2dv.garden.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummaryResponse(
    UUID id,
    String title,
    String handle,
    String vendor,
    String featuredImageUrl,
    BigDecimal priceMin,
    BigDecimal priceMax,
    BigDecimal compareAtPriceMin,
    BigDecimal compareAtPriceMax
) {}
