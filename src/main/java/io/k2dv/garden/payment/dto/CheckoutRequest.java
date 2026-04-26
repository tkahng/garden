package io.k2dv.garden.payment.dto;

import java.util.UUID;

public record CheckoutRequest(String discountCode, String giftCardCode, UUID shippingRateId) {}
