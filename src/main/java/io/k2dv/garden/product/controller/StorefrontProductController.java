package io.k2dv.garden.product.controller;

import io.k2dv.garden.product.dto.ProductDetailResponse;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.product.dto.StorefrontProductFilterRequest;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class StorefrontProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<ProductSummaryResponse>>> list(
            @RequestParam(required = false) String titleContains,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        var filter = new StorefrontProductFilterRequest(titleContains, vendor, productType);
        return ResponseEntity.ok(ApiResponse.of(
                productService.listStorefront(filter, PageRequest.of(page, clampedSize, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.of(productService.getByHandle(handle)));
    }
}
