package io.k2dv.garden.product.controller;

import io.k2dv.garden.product.dto.ProductDetailResponse;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class StorefrontProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(productService.listStorefront(cursor));
    }

    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.of(productService.getByHandle(handle)));
    }
}
