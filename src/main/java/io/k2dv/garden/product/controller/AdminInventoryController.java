package io.k2dv.garden.product.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.product.dto.InventoryItemResponse;
import io.k2dv.garden.product.dto.UpdateInventoryRequest;
import io.k2dv.garden.inventory.model.InventoryItem;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Products", description = "Admin product inventory")
@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryItemRepository inventoryRepo;

    @PatchMapping("/{itemId}")
    @HasPermission("inventory:write")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> update(
            @PathVariable UUID itemId, @Valid @RequestBody UpdateInventoryRequest req) {
        InventoryItem inv = inventoryRepo.findById(itemId)
            .orElseThrow(() -> new NotFoundException("INVENTORY_NOT_FOUND", "Inventory item not found"));
        inv.setRequiresShipping(req.requiresShipping());
        inv = inventoryRepo.save(inv);
        return ResponseEntity.ok(ApiResponse.of(
            new InventoryItemResponse(inv.getId(), inv.getVariantId(), inv.isRequiresShipping())));
    }
}
