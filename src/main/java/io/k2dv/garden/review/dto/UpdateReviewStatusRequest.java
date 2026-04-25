package io.k2dv.garden.review.dto;

import io.k2dv.garden.review.model.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateReviewStatusRequest(@NotNull ReviewStatus status) {}
