package io.k2dv.garden.wishlist.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddWishlistItemRequest(@NotNull UUID productId) {}
