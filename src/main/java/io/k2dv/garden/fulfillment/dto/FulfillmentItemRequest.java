package io.k2dv.garden.fulfillment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record FulfillmentItemRequest(
    @NotNull UUID orderItemId,
    @Positive int quantity
) {}
