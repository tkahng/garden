package io.k2dv.garden.b2b.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ResolvedPriceResponse(
    UUID variantId,
    UUID companyId,
    int qty,
    BigDecimal price,
    String currency,
    UUID priceListId,
    boolean contractPrice
) {}
