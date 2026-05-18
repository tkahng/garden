package io.k2dv.garden.quote.repository;

import io.k2dv.garden.quote.model.QuoteRequest;
import io.k2dv.garden.quote.model.QuoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, UUID>, JpaSpecificationExecutor<QuoteRequest> {
    Optional<QuoteRequest> findByOrderId(UUID orderId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE QuoteRequest q SET q.status = :to WHERE q.status = :from AND q.expiresAt < :now")
    int expireByStatus(@Param("from") QuoteStatus from, @Param("to") QuoteStatus to, @Param("now") Instant now);

    @Query("SELECT q FROM QuoteRequest q WHERE q.status = :status AND q.expiresAt < :now")
    List<QuoteRequest> findExpiredByStatus(@Param("status") QuoteStatus status, @Param("now") Instant now);
}
