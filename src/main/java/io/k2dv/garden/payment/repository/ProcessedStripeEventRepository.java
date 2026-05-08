package io.k2dv.garden.payment.repository;

import io.k2dv.garden.payment.model.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, String> {

    @Modifying
    @Query("DELETE FROM ProcessedStripeEvent e WHERE e.processedAt < :before")
    void deleteOlderThan(Instant before);
}
