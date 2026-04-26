package io.k2dv.garden.wishlist.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WishlistItemResponse(
    UUID id,
    UUID productId,
    String title,
    String handle,
    String featuredImageUrl,
    BigDecimal priceMin
) {}
