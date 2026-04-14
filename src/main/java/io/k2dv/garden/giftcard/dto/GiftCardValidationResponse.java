package io.k2dv.garden.giftcard.dto;

import java.math.BigDecimal;

public record GiftCardValidationResponse(
    boolean valid,
    String code,
    BigDecimal currentBalance,
    String currency,
    String message
) {}
