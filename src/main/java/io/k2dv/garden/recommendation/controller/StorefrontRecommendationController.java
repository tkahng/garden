package io.k2dv.garden.recommendation.controller;

import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.recommendation.service.RecommendationService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{handle}/related")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "Related product recommendations")
@SecurityRequirements({})
public class StorefrontRecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getRelated(
            @PathVariable String handle,
            @RequestParam(defaultValue = "4") int limit) {
        return ResponseEntity.ok(ApiResponse.of(recommendationService.findRelated(handle, limit)));
    }
}
