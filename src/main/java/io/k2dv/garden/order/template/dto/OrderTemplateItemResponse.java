package io.k2dv.garden.order.template.dto;

import io.k2dv.garden.order.template.model.OrderTemplateItem;

import java.time.Instant;
import java.util.UUID;

public record OrderTemplateItemResponse(
    UUID id,
    UUID variantId,
    String variantTitle,
    int quantity,
    Instant createdAt
) {
    public static OrderTemplateItemResponse from(OrderTemplateItem item, String variantTitle) {
        return new OrderTemplateItemResponse(
            item.getId(), item.getVariantId(), variantTitle,
            item.getQuantity(), item.getCreatedAt()
        );
    }
}
