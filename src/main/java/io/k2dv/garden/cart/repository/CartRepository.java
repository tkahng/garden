package io.k2dv.garden.cart.repository;

import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUserIdAndStatus(UUID userId, CartStatus status);
}
