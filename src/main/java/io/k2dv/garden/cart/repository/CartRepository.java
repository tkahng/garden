package io.k2dv.garden.cart.repository;

import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUserIdAndStatus(UUID userId, CartStatus status);
    Optional<Cart> findBySessionIdAndStatus(UUID sessionId, CartStatus status);
    Optional<Cart> findBySessionId(UUID sessionId);

    @Query(value = """
        SELECT c.* FROM checkout.carts c
        WHERE c.status = 'ACTIVE'
        AND c.updated_at < :cutoff
        AND c.abandoned_reminder_sent_at IS NULL
        AND EXISTS (SELECT 1 FROM checkout.cart_items ci WHERE ci.cart_id = c.id)
        AND (
            c.user_id IS NOT NULL
            OR (c.guest_email IS NOT NULL AND c.session_id IS NOT NULL)
        )
        """, nativeQuery = true)
    List<Cart> findAbandonedCarts(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = """
        DELETE FROM checkout.cart_items WHERE cart_id IN (
            SELECT id FROM checkout.carts WHERE status IN ('ABANDONED', 'CHECKED_OUT')
            AND updated_at < :cutoff AND session_id IS NOT NULL
        )
        """, nativeQuery = true)
    int deleteGuestCartItemsOlderThan(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = """
        DELETE FROM checkout.carts WHERE status IN ('ABANDONED', 'CHECKED_OUT')
        AND updated_at < :cutoff AND session_id IS NOT NULL
        """, nativeQuery = true)
    int deleteGuestCartsOlderThan(@Param("cutoff") Instant cutoff);
}
