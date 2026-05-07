package io.k2dv.garden.product.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.b2b.dto.VariantPriceTiersResponse;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.b2b.service.PriceListService;
import io.k2dv.garden.product.dto.ProductDetailResponse;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.product.dto.StorefrontProductFilterRequest;
import io.k2dv.garden.product.dto.VariantLookupResponse;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Public storefront product catalog")
@SecurityRequirements({})
public class StorefrontProductController {

    private final ProductService productService;
    private final PriceListService priceListService;
    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<ProductSummaryResponse>>> list(
            @RequestParam(required = false) String titleContains,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        Sort sort = switch (sortBy == null ? "newest" : sortBy) {
            case "title_asc" -> Sort.by("title").ascending();
            case "title_desc" -> Sort.by("title").descending();
            case "oldest" -> Sort.by("createdAt").ascending();
            default -> Sort.by("createdAt").descending();
        };
        var filter = new StorefrontProductFilterRequest(titleContains, vendor, productType, companyId);
        return ResponseEntity.ok(ApiResponse.of(
                productService.listStorefront(filter, PageRequest.of(page, clampedSize, sort))));
    }

    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.of(productService.getByHandle(handle)));
    }

    @GetMapping("/variants/lookup")
    public ResponseEntity<ApiResponse<VariantLookupResponse>> lookupBySku(
            @RequestParam String sku) {
        return ResponseEntity.ok(ApiResponse.of(productService.lookupBySku(sku)));
    }

    @Authenticated
    @GetMapping("/{handle}/tiers")
    public ResponseEntity<ApiResponse<List<VariantPriceTiersResponse>>> getVariantTiers(
            @PathVariable String handle,
            @RequestParam UUID companyId,
            @CurrentUser User user) {
        companyService.requireMemberAccess(companyId, user.getId());
        return ResponseEntity.ok(ApiResponse.of(priceListService.getProductTiers(companyId, handle)));
    }
}
