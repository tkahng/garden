package io.k2dv.garden.quote.repository;

import io.k2dv.garden.quote.model.QuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteItemRepository extends JpaRepository<QuoteItem, UUID> {

    List<QuoteItem> findByQuoteRequestId(UUID quoteRequestId);

    Optional<QuoteItem> findByIdAndQuoteRequestId(UUID id, UUID quoteRequestId);
}
