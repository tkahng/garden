package io.k2dv.garden.review.dto;

import java.math.BigDecimal;

public record ReviewSummaryResponse(
    BigDecimal averageRating,
    long reviewCount
) {}
