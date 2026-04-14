package io.k2dv.garden.order.dto;

import io.k2dv.garden.order.model.OrderEvent;
import io.k2dv.garden.order.model.OrderEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OrderEventResponse(
    UUID id,
    UUID orderId,
    OrderEventType type,
    String message,
    UUID authorId,
    String authorName,
    Map<String, Object> metadata,
    Instant createdAt
) {
    public static OrderEventResponse from(OrderEvent e) {
        return new OrderEventResponse(
            e.getId(), e.getOrderId(), e.getType(), e.getMessage(),
            e.getAuthorId(), e.getAuthorName(), e.getMetadata(), e.getCreatedAt()
        );
    }
}
