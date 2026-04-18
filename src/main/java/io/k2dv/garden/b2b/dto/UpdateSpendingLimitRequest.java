package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateSpendingLimitRequest(
    @Positive BigDecimal spendingLimit
) {}
