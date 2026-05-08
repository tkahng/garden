package io.k2dv.garden.order.event;

public record OrderCancelledEvent(
    String to,
    String orderRef,
    String frontendUrl
) {}
