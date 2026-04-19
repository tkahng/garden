package io.k2dv.garden.b2b.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerPriceEntryResponse(
    UUID variantId,
    String productTitle,
    String productHandle,
    String variantTitle,
    String sku,
    BigDecimal retailPrice,
    BigDecimal contractPrice,
    int minQty
) {}
