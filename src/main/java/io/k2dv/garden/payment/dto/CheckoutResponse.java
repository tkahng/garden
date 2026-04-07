package io.k2dv.garden.payment.dto;

import java.util.UUID;

public record CheckoutResponse(String checkoutUrl, UUID orderId) {}
