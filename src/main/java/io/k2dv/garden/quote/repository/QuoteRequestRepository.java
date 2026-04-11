package io.k2dv.garden.quote.repository;

import io.k2dv.garden.quote.model.QuoteRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, UUID>, JpaSpecificationExecutor<QuoteRequest> {
}
