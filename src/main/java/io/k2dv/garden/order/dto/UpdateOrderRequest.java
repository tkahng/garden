package io.k2dv.garden.order.dto;

public record UpdateOrderRequest(
    String adminNotes,
    String shippingAddress
) {}
