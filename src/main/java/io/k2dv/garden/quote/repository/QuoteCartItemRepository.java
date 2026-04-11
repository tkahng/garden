package io.k2dv.garden.quote.repository;

import io.k2dv.garden.quote.model.QuoteCartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteCartItemRepository extends JpaRepository<QuoteCartItem, UUID> {

    List<QuoteCartItem> findByQuoteCartId(UUID quoteCartId);

    Optional<QuoteCartItem> findByIdAndQuoteCartId(UUID id, UUID quoteCartId);

    Optional<QuoteCartItem> findByQuoteCartIdAndVariantId(UUID quoteCartId, UUID variantId);
}
