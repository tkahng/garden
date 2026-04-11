package io.k2dv.garden.quote.dto;

import java.util.UUID;

public record QuoteAcceptResponse(
    String checkoutUrl,
    UUID orderId
) {}
