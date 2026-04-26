package io.k2dv.garden.review.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.review.dto.CreateReviewRequest;
import io.k2dv.garden.review.dto.ReviewResponse;
import io.k2dv.garden.review.service.ProductReviewService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product review endpoints")
public class StorefrontReviewController {

    private final ProductReviewService reviewService;

    @GetMapping
    @SecurityRequirements({})
    public ResponseEntity<ApiResponse<PagedResult<ReviewResponse>>> list(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int clampedSize = Math.min(size, 50);
        return ResponseEntity.ok(ApiResponse.of(
            reviewService.listReviews(productId, PageRequest.of(page, clampedSize))));
    }

    @PostMapping
    @Authenticated
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @PathVariable UUID productId,
            @CurrentUser User user,
            @Valid @RequestBody CreateReviewRequest req) {
        ReviewResponse response = reviewService.createReview(productId, user.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
