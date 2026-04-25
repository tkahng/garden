package io.k2dv.garden.review.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.review.dto.ReviewResponse;
import io.k2dv.garden.review.dto.UpdateReviewStatusRequest;
import io.k2dv.garden.review.service.ProductReviewService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@Tag(name = "Admin Reviews", description = "Admin product review moderation")
@HasPermission("MANAGE_PRODUCTS")
public class AdminReviewController {

    private final ProductReviewService reviewService;

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReviewStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.of(reviewService.updateStatus(id, req)));
    }
}
