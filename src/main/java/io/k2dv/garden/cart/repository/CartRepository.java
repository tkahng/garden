package io.k2dv.garden.cart.repository;

import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUserIdAndStatus(UUID userId, CartStatus status);
    Optional<Cart> findBySessionIdAndStatus(UUID sessionId, CartStatus status);

    @Query(value = """
        SELECT c.* FROM checkout.carts c
        WHERE c.status = 'ACTIVE'
        AND c.updated_at < :cutoff
        AND c.abandoned_reminder_sent_at IS NULL
        AND c.user_id IS NOT NULL
        AND EXISTS (SELECT 1 FROM checkout.cart_items ci WHERE ci.cart_id = c.id)
        """, nativeQuery = true)
    List<Cart> findAbandonedCarts(@Param("cutoff") Instant cutoff);
}
