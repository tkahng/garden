package io.k2dv.garden.cart.dto;

import io.k2dv.garden.cart.model.CartStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
    UUID id,
    CartStatus status,
    List<CartItemResponse> items,
    Instant createdAt
) {}
