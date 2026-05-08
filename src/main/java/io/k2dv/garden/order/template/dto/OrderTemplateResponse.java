package io.k2dv.garden.order.template.dto;

import io.k2dv.garden.order.template.model.OrderTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderTemplateResponse(
    UUID id,
    UUID userId,
    String name,
    List<OrderTemplateItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {
    public static OrderTemplateResponse from(OrderTemplate t, List<OrderTemplateItemResponse> items) {
        return new OrderTemplateResponse(
            t.getId(), t.getUserId(), t.getName(),
            items, t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}
