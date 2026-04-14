package io.k2dv.garden.giftcard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateGiftCardRequest(
    String code,
    @NotNull @Positive BigDecimal initialBalance,
    String currency,
    Instant expiresAt,
    String note,
    UUID purchaserUserId,
    String recipientEmail
) {}
