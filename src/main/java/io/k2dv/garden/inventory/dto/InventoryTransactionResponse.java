package io.k2dv.garden.inventory.dto;

import io.k2dv.garden.inventory.model.InventoryTransactionReason;

import java.time.Instant;
import java.util.UUID;

public record InventoryTransactionResponse(
    UUID id,
    UUID inventoryItemId,
    UUID locationId,
    String locationName,
    int quantity,
    InventoryTransactionReason reason,
    String note,
    Instant createdAt
) {}
