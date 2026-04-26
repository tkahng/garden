package io.k2dv.garden.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating,
    String title,
    String body
) {}
