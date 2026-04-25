package io.k2dv.garden.order.dto;

import io.k2dv.garden.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    UUID userId,
    String guestEmail,
    OrderStatus status,
    BigDecimal totalAmount,
    String currency,
    String stripeSessionId,
    UUID discountId,
    BigDecimal discountAmount,
    UUID giftCardId,
    BigDecimal giftCardAmount,
    String adminNotes,
    String shippingAddress,
    BigDecimal shippingCost,
    UUID shippingRateId,
    List<OrderItemResponse> items,
    Instant createdAt
) {}
