package io.k2dv.garden.collection.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.collection.dto.request.AddCollectionProductRequest;
import io.k2dv.garden.collection.dto.request.CollectionFilterRequest;
import io.k2dv.garden.collection.dto.request.CollectionStatusRequest;
import io.k2dv.garden.collection.dto.request.CreateCollectionRequest;
import io.k2dv.garden.collection.dto.request.CreateCollectionRuleRequest;
import io.k2dv.garden.collection.dto.request.UpdateCollectionProductPositionRequest;
import io.k2dv.garden.collection.dto.request.UpdateCollectionRequest;
import io.k2dv.garden.collection.dto.response.AdminCollectionResponse;
import io.k2dv.garden.collection.dto.response.AdminCollectionSummaryResponse;
import io.k2dv.garden.collection.dto.response.CollectionProductResponse;
import io.k2dv.garden.collection.dto.response.CollectionRuleResponse;
import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import io.k2dv.garden.collection.service.CollectionService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Collections", description = "Admin collection management")
@RestController
@RequestMapping("/api/v1/admin/collections")
@RequiredArgsConstructor
public class AdminCollectionController {

    private final CollectionService collectionService;

    @PostMapping
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<AdminCollectionResponse>> create(
            @Valid @RequestBody CreateCollectionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(collectionService.create(req)));
    }

    @GetMapping
    @HasPermission("collection:read")
    public ResponseEntity<ApiResponse<PagedResult<AdminCollectionSummaryResponse>>> list(
            @RequestParam(required = false) CollectionType collectionType,
            @RequestParam(required = false) CollectionStatus status,
            @RequestParam(required = false) String titleContains,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        var filter = new CollectionFilterRequest(collectionType, status, titleContains);
        return ResponseEntity.ok(ApiResponse.of(
                collectionService.listAdmin(filter, PageRequest.of(page, clampedSize, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    @HasPermission("collection:read")
    public ResponseEntity<ApiResponse<AdminCollectionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.getAdmin(id)));
    }

    @PatchMapping("/{id}")
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<AdminCollectionResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateCollectionRequest req) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.update(id, req)));
    }

    @PatchMapping("/{id}/status")
    @HasPermission("collection:publish")
    public ResponseEntity<ApiResponse<AdminCollectionResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody CollectionStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.changeStatus(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("collection:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        collectionService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/products")
    @HasPermission("collection:read")
    public ResponseEntity<ApiResponse<PagedResult<CollectionProductResponse>>> listProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.of(
                collectionService.listProducts(id, PageRequest.of(page, clampedSize))));
    }

    @PostMapping("/{id}/products")
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<CollectionProductResponse>> addProduct(
            @PathVariable UUID id, @Valid @RequestBody AddCollectionProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(collectionService.addProduct(id, req)));
    }

    @DeleteMapping("/{id}/products/{productId}")
    @HasPermission("collection:write")
    public ResponseEntity<Void> removeProduct(@PathVariable UUID id, @PathVariable UUID productId) {
        collectionService.removeProduct(id, productId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/products/{productId}/position")
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<CollectionProductResponse>> updateProductPosition(
            @PathVariable UUID id, @PathVariable UUID productId,
            @Valid @RequestBody UpdateCollectionProductPositionRequest req) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.updateProductPosition(id, productId, req)));
    }

    @GetMapping("/{id}/rules")
    @HasPermission("collection:read")
    public ResponseEntity<ApiResponse<List<CollectionRuleResponse>>> listRules(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.listRules(id)));
    }

    @PostMapping("/{id}/rules")
    @HasPermission("collection:write")
    public ResponseEntity<ApiResponse<CollectionRuleResponse>> addRule(
            @PathVariable UUID id, @Valid @RequestBody CreateCollectionRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(collectionService.addRule(id, req)));
    }

    @DeleteMapping("/{id}/rules/{ruleId}")
    @HasPermission("collection:write")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id, @PathVariable UUID ruleId) {
        collectionService.deleteRule(id, ruleId);
        return ResponseEntity.noContent().build();
    }
}
