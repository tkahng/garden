package io.k2dv.garden.payment.dto;

import io.k2dv.garden.order.model.OrderStatus;

import java.util.UUID;

public record CheckoutReturnResponse(UUID orderId, OrderStatus status) {}
