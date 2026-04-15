package io.k2dv.garden.fulfillment.dto;

import io.k2dv.garden.fulfillment.model.FulfillmentStatus;

public record UpdateFulfillmentRequest(
    FulfillmentStatus status,
    String trackingNumber,
    String trackingCompany,
    String trackingUrl,
    String note
) {}
