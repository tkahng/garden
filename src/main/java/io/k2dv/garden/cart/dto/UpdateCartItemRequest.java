package io.k2dv.garden.cart.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
    @Min(1) int quantity
) {}
