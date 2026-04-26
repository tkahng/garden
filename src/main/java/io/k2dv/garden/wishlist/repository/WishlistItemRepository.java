package io.k2dv.garden.wishlist.repository;

import io.k2dv.garden.wishlist.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {
    List<WishlistItem> findByWishlistId(UUID wishlistId);
    Optional<WishlistItem> findByWishlistIdAndProductId(UUID wishlistId, UUID productId);
    boolean existsByWishlistIdAndProductId(UUID wishlistId, UUID productId);
    void deleteByWishlistIdAndProductId(UUID wishlistId, UUID productId);
}
