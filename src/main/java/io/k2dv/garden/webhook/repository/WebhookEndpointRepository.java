package io.k2dv.garden.webhook.repository;

import io.k2dv.garden.webhook.model.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByActiveTrue();

    @Query(value = """
        SELECT * FROM webhook.endpoints
        WHERE is_active = true
          AND (events = '{}' OR :event = ANY(events))
        """, nativeQuery = true)
    List<WebhookEndpoint> findActiveByEvent(@Param("event") String event);
}
