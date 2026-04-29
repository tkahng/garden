package io.k2dv.garden.webhook.dto;

import io.k2dv.garden.webhook.model.WebhookDelivery;
import io.k2dv.garden.webhook.model.WebhookDeliveryStatus;
import io.k2dv.garden.webhook.model.WebhookEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebhookDeliveryResponse(
    UUID id,
    UUID endpointId,
    WebhookEventType eventType,
    Map<String, Object> payload,
    WebhookDeliveryStatus status,
    int attemptCount,
    Instant lastAttemptedAt,
    Instant nextRetryAt,
    Integer httpStatus,
    String responseBody,
    Instant createdAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery d) {
        return new WebhookDeliveryResponse(
            d.getId(), d.getEndpointId(), d.getEventType(), d.getPayload(),
            d.getStatus(), d.getAttemptCount(), d.getLastAttemptedAt(),
            d.getNextRetryAt(), d.getHttpStatus(), d.getResponseBody(), d.getCreatedAt()
        );
    }
}
