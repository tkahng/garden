package io.k2dv.garden.review.dto;

import io.k2dv.garden.review.model.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    UUID productId,
    UUID userId,
    String reviewerName,
    int rating,
    String title,
    String body,
    boolean verifiedPurchase,
    ReviewStatus status,
    Instant createdAt
) {}
