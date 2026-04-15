package io.k2dv.garden.giftcard.dto;

import java.time.Instant;

public record UpdateGiftCardRequest(
    Instant expiresAt,
    String note,
    String recipientEmail
) {}
