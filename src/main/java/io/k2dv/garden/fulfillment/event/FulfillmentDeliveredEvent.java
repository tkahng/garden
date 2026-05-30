package io.k2dv.garden.fulfillment.event;

public record FulfillmentDeliveredEvent(
    String to,
    String orderRef,
    String frontendUrl
) {}
