package io.k2dv.garden.giftcard.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GiftCardTransactionRequest(
    @NotNull BigDecimal delta,
    String note
) {}
