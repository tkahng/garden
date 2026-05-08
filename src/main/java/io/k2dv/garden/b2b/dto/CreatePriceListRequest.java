package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.PriceListAdjustmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreatePriceListRequest(
    @NotNull UUID companyId,
    @NotBlank String name,
    String currency,
    Integer priority,
    Instant startsAt,
    Instant endsAt,
    PriceListAdjustmentType adjustmentType,
    @Positive BigDecimal adjustmentValue
) {}
