package io.k2dv.garden.order.event;

import java.math.BigDecimal;
import java.util.List;

public record OrderConfirmedEvent(
    String to,
    String orderRef,
    BigDecimal total,
    String currency,
    List<String> itemLines,
    String frontendUrl
) {}
