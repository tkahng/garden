package io.k2dv.garden.webhook.dto;

import io.k2dv.garden.webhook.model.WebhookEndpoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookEndpointResponse(
    UUID id,
    String url,
    String description,
    List<String> events,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static WebhookEndpointResponse from(WebhookEndpoint e) {
        return new WebhookEndpointResponse(
            e.getId(), e.getUrl(), e.getDescription(),
            e.getEvents(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
