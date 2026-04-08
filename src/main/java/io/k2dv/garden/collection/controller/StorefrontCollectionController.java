package io.k2dv.garden.collection.controller;

import io.k2dv.garden.collection.dto.response.CollectionDetailResponse;
import io.k2dv.garden.collection.dto.response.CollectionProductResponse;
import io.k2dv.garden.collection.dto.response.CollectionSummaryResponse;
import io.k2dv.garden.collection.service.CollectionService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
@Tag(name = "Collections", description = "Public storefront collection catalog")
@SecurityRequirements({})
public class StorefrontCollectionController {

    private final CollectionService collectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<CollectionSummaryResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.of(
                collectionService.listStorefront(PageRequest.of(page, clampedSize, Sort.by("createdAt").ascending()))));
    }

    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<CollectionDetailResponse>> getByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.of(collectionService.getByHandle(handle)));
    }

    @GetMapping("/{handle}/products")
    public ResponseEntity<ApiResponse<PagedResult<CollectionProductResponse>>> listProducts(
            @PathVariable String handle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.of(
                collectionService.listProductsStorefront(handle, PageRequest.of(page, clampedSize))));
    }
}
