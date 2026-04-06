package io.k2dv.garden.product.dto;

import java.util.UUID;

public record InventoryItemResponse(UUID id, UUID variantId, boolean requiresShipping) {}
