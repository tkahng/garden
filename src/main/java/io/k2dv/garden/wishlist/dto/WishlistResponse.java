package io.k2dv.garden.wishlist.dto;

import java.util.List;
import java.util.UUID;

public record WishlistResponse(
    UUID id,
    List<WishlistItemResponse> items
) {}
