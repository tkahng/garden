package io.k2dv.garden.product.dto;

import io.k2dv.garden.inventory.model.FulfillmentType;
import io.k2dv.garden.inventory.model.InventoryPolicy;

import java.math.BigDecimal;

public record UpdateVariantRequest(
    BigDecimal price,
    BigDecimal compareAtPrice,
    String sku,
    String barcode,
    BigDecimal weight,
    String weightUnit,
    FulfillmentType fulfillmentType,
    InventoryPolicy inventoryPolicy,
    Integer leadTimeDays
) {}
