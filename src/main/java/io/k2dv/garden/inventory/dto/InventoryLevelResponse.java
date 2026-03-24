package io.k2dv.garden.inventory.dto;

import java.util.UUID;

public record InventoryLevelResponse(
    UUID id,
    UUID inventoryItemId,
    UUID locationId,
    String locationName,
    int quantityOnHand,
    int quantityCommitted
) {}
