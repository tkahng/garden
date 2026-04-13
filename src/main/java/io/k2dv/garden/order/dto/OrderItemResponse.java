package io.k2dv.garden.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
    UUID id,
    UUID variantId,
    int quantity,
    BigDecimal unitPrice,
    OrderItemProductInfo product
) {}
