package io.k2dv.garden.fulfillment.dto;

import io.k2dv.garden.fulfillment.model.Fulfillment;
import io.k2dv.garden.fulfillment.model.FulfillmentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FulfillmentResponse(
    UUID id,
    UUID orderId,
    FulfillmentStatus status,
    String trackingNumber,
    String trackingCompany,
    String trackingUrl,
    String note,
    List<FulfillmentItemResponse> items,
    Instant createdAt
) {
    public static FulfillmentResponse from(Fulfillment f, List<FulfillmentItemResponse> items) {
        return new FulfillmentResponse(
            f.getId(), f.getOrderId(), f.getStatus(),
            f.getTrackingNumber(), f.getTrackingCompany(), f.getTrackingUrl(),
            f.getNote(), items, f.getCreatedAt()
        );
    }
}
