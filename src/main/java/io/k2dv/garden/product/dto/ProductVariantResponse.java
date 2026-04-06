package io.k2dv.garden.product.dto;

import io.k2dv.garden.inventory.model.FulfillmentType;
import io.k2dv.garden.inventory.model.InventoryPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductVariantResponse(
    UUID id,
    String title,
    String sku,
    BigDecimal price,
    BigDecimal compareAtPrice,
    List<OptionValueLabel> optionValues,
    FulfillmentType fulfillmentType,
    InventoryPolicy inventoryPolicy,
    int leadTimeDays
) {}
