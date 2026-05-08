package io.k2dv.garden.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReturnRequestItemInput(
    @NotNull UUID orderItemId,
    @Min(1) int quantity
) {}
