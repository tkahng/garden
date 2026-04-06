package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateVariantRequest(
    @NotNull BigDecimal price,
    BigDecimal compareAtPrice,
    String sku,
    String barcode,
    BigDecimal weight,
    String weightUnit,
    List<UUID> optionValueIds
) {}
