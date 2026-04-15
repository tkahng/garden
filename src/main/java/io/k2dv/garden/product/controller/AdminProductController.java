package io.k2dv.garden.product.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.*;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Products", description = "Admin product management")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final VariantService variantService;
    private final OptionService optionService;
    private final ProductImageService imageService;

    // Product CRUD
    @PostMapping
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<AdminProductResponse>> create(@Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(productService.create(req)));
    }

    @GetMapping
    @HasPermission("product:read")
    public ResponseEntity<ApiResponse<PagedResult<AdminProductResponse>>> list(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String titleContains,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        var filter = new ProductFilterRequest(status, titleContains, vendor, productType);
        return ResponseEntity.ok(ApiResponse.of(
                productService.listAdmin(filter, PageRequest.of(page, clampedSize, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    @HasPermission("product:read")
    public ResponseEntity<ApiResponse<AdminProductResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(productService.getAdmin(id)));
    }

    @PatchMapping("/{id}")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<AdminProductResponse>> update(
            @PathVariable UUID id, @RequestBody UpdateProductRequest req) {
        return ResponseEntity.ok(ApiResponse.of(productService.update(id, req)));
    }

    @PatchMapping("/{id}/status")
    @HasPermission("product:publish")
    public ResponseEntity<ApiResponse<AdminProductResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ProductStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.of(productService.changeStatus(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("product:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // Variants
    @PostMapping("/{id}/variants")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<AdminVariantResponse>> createVariant(
            @PathVariable UUID id, @Valid @RequestBody CreateVariantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(variantService.create(id, req)));
    }

    @PatchMapping("/{id}/variants/{vId}")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<AdminVariantResponse>> updateVariant(
            @PathVariable UUID id, @PathVariable UUID vId, @RequestBody UpdateVariantRequest req) {
        return ResponseEntity.ok(ApiResponse.of(variantService.update(id, vId, req)));
    }

    @DeleteMapping("/{id}/variants/{vId}")
    @HasPermission("product:delete")
    public ResponseEntity<Void> deleteVariant(@PathVariable UUID id, @PathVariable UUID vId) {
        variantService.softDelete(id, vId);
        return ResponseEntity.noContent().build();
    }

    // Images — NOTE: /positions must be declared BEFORE /{imgId} to avoid routing conflict
    @PostMapping("/{id}/images")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<ProductImageResponse>> addImage(
            @PathVariable UUID id, @Valid @RequestBody CreateImageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(imageService.addImage(id, req)));
    }

    @PatchMapping("/{id}/images/positions")
    @HasPermission("product:write")
    public ResponseEntity<Void> reorderImages(
            @PathVariable UUID id, @RequestBody List<ImagePositionItem> items) {
        imageService.reorderImages(id, items);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/images/{imgId}")
    @HasPermission("product:delete")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id, @PathVariable UUID imgId) {
        imageService.deleteImage(id, imgId);
        return ResponseEntity.noContent().build();
    }

    // Options
    @PostMapping("/{id}/options")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<ProductOptionResponse>> createOption(
            @PathVariable UUID id, @Valid @RequestBody CreateOptionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(optionService.createOption(id, req)));
    }

    @PatchMapping("/{id}/options/{optId}")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<ProductOptionResponse>> renameOption(
            @PathVariable UUID id, @PathVariable UUID optId, @Valid @RequestBody RenameOptionRequest req) {
        return ResponseEntity.ok(ApiResponse.of(optionService.renameOption(id, optId, req)));
    }

    @DeleteMapping("/{id}/options/{optId}")
    @HasPermission("product:delete")
    public ResponseEntity<Void> deleteOption(@PathVariable UUID id, @PathVariable UUID optId) {
        optionService.deleteOption(id, optId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/options/{optId}/values")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<ProductOptionValueResponse>> createOptionValue(
            @PathVariable UUID id, @PathVariable UUID optId,
            @Valid @RequestBody CreateOptionValueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.of(optionService.createOptionValue(id, optId, req)));
    }

    @PatchMapping("/{id}/options/{optId}/values/{valId}")
    @HasPermission("product:write")
    public ResponseEntity<ApiResponse<ProductOptionValueResponse>> renameOptionValue(
            @PathVariable UUID id, @PathVariable UUID optId, @PathVariable UUID valId,
            @Valid @RequestBody RenameOptionValueRequest req) {
        return ResponseEntity.ok(ApiResponse.of(optionService.renameOptionValue(optId, valId, req)));
    }

    @DeleteMapping("/{id}/options/{optId}/values/{valId}")
    @HasPermission("product:delete")
    public ResponseEntity<Void> deleteOptionValue(
            @PathVariable UUID id, @PathVariable UUID optId, @PathVariable UUID valId) {
        optionService.deleteOptionValue(id, optId, valId);
        return ResponseEntity.noContent().build();
    }

    // Inventory GET
    @GetMapping("/{id}/inventory")
    @HasPermission("inventory:read")
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> getInventory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(variantService.getInventoryForProduct(id)));
    }
}
