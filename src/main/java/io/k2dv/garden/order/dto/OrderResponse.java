package io.k2dv.garden.order.dto;

import io.k2dv.garden.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    UUID userId,
    OrderStatus status,
    BigDecimal totalAmount,
    String currency,
    String stripeSessionId,
    UUID discountId,
    BigDecimal discountAmount,
    String adminNotes,
    String shippingAddress,
    List<OrderItemResponse> items,
    Instant createdAt
) {}
