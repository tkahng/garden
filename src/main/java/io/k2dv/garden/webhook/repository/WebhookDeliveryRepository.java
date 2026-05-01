package io.k2dv.garden.webhook.repository;

import io.k2dv.garden.webhook.model.WebhookDelivery;
import io.k2dv.garden.webhook.model.WebhookDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Page<WebhookDelivery> findByEndpointId(UUID endpointId, Pageable pageable);

    @Query("""
        SELECT d FROM WebhookDelivery d
        WHERE d.status = 'PENDING'
           OR (d.status = 'FAILED' AND d.nextRetryAt IS NOT NULL AND d.nextRetryAt <= :now)
        ORDER BY d.createdAt ASC
        """)
    List<WebhookDelivery> findDispatchable(@Param("now") Instant now);
}
