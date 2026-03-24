package io.k2dv.garden.inventory.dto;

import io.k2dv.garden.inventory.model.FulfillmentType;
import io.k2dv.garden.inventory.model.InventoryPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateVariantFulfillmentRequest(
    @NotNull FulfillmentType fulfillmentType,
    @NotNull InventoryPolicy inventoryPolicy,
    @Min(0) int leadTimeDays
) {}
