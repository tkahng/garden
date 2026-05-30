package io.k2dv.garden.fulfillment.event;

public record FulfillmentShippedEvent(
    String to,
    String orderRef,
    String trackingNumber,
    String trackingCompany,
    String trackingUrl,
    String frontendUrl
) {}
