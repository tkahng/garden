package io.k2dv.garden.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReceiveStockRequest(
    @NotNull UUID locationId,
    @Min(1) int quantity,
    String note
) {}
