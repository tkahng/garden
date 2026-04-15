package io.k2dv.garden.fulfillment.dto;

import io.k2dv.garden.fulfillment.model.FulfillmentItem;

import java.util.UUID;

public record FulfillmentItemResponse(
    UUID id,
    UUID orderItemId,
    int quantity
) {
    public static FulfillmentItemResponse from(FulfillmentItem item) {
        return new FulfillmentItemResponse(item.getId(), item.getOrderItemId(), item.getQuantity());
    }
}
