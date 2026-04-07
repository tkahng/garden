package io.k2dv.garden.cart.repository;

import io.k2dv.garden.cart.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartId(UUID cartId);
    Optional<CartItem> findByCartIdAndVariantId(UUID cartId, UUID variantId);
    Optional<CartItem> findByIdAndCartId(UUID id, UUID cartId);
}
