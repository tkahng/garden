package io.k2dv.garden.inventory.controller;

import io.k2dv.garden.inventory.dto.*;
import io.k2dv.garden.inventory.service.InventoryService;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController("adminInventoryStockController")
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/variants/{variantId}/levels")
    @HasPermission("inventory:read")
    public ResponseEntity<ApiResponse<List<InventoryLevelResponse>>> getLevels(
            @PathVariable UUID variantId) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.getLevels(variantId)));
    }

    @PostMapping("/variants/{variantId}/receive")
    @HasPermission("inventory:write")
    public ResponseEntity<ApiResponse<InventoryLevelResponse>> receiveStock(
            @PathVariable UUID variantId,
            @RequestBody @Valid ReceiveStockRequest req) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.receiveStock(variantId, req)));
    }

    @PostMapping("/variants/{variantId}/adjust")
    @HasPermission("inventory:write")
    public ResponseEntity<ApiResponse<InventoryLevelResponse>> adjustStock(
            @PathVariable UUID variantId,
            @RequestBody @Valid AdjustStockRequest req) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.adjustStock(variantId, req)));
    }

    @GetMapping("/variants/{variantId}/transactions")
    @HasPermission("inventory:read")
    public ResponseEntity<ApiResponse<PagedResult<InventoryTransactionResponse>>> listTransactions(
            @PathVariable UUID variantId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.listTransactions(variantId, locationId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @PatchMapping("/variants/{variantId}/fulfillment")
    @HasPermission("inventory:write")
    public ResponseEntity<ApiResponse<AdminVariantResponse>> updateFulfillment(
            @PathVariable UUID variantId,
            @RequestBody @Valid UpdateVariantFulfillmentRequest req) {
        return ResponseEntity.ok(ApiResponse.of(inventoryService.updateVariantFulfillment(variantId, req)));
    }
}
