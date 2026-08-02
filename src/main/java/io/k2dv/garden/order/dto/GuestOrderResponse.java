package io.k2dv.garden.order.dto;

import io.k2dv.garden.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GuestOrderResponse(
    UUID id,
    String guestEmail,
    OrderStatus status,
    BigDecimal totalAmount,
    String currency,
    BigDecimal discountAmount,
    BigDecimal giftCardAmount,
    BigDecimal shippingCost,
    BigDecimal taxAmount,
    String shippingAddress,
    String poNumber,
    List<OrderItemResponse> items,
    Instant createdAt
) {}
