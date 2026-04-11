package io.k2dv.garden.quote.repository;

import io.k2dv.garden.quote.model.QuoteCart;
import io.k2dv.garden.quote.model.QuoteCartStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuoteCartRepository extends JpaRepository<QuoteCart, UUID> {

    Optional<QuoteCart> findByUserIdAndStatus(UUID userId, QuoteCartStatus status);
}
