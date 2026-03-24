package io.k2dv.garden.inventory.dto;

import io.k2dv.garden.inventory.model.InventoryTransactionReason;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdjustStockRequest(
    @NotNull UUID locationId,
    int delta,
    @NotNull InventoryTransactionReason reason,
    String note
) {}
