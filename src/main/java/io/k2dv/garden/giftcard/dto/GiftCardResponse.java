package io.k2dv.garden.giftcard.dto;

import io.k2dv.garden.giftcard.model.GiftCard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GiftCardResponse(
    UUID id,
    String code,
    BigDecimal initialBalance,
    BigDecimal currentBalance,
    String currency,
    boolean isActive,
    Instant expiresAt,
    String note,
    UUID purchaserUserId,
    String recipientEmail,
    Instant createdAt
) {
    public static GiftCardResponse from(GiftCard g) {
        return new GiftCardResponse(
            g.getId(), g.getCode(), g.getInitialBalance(), g.getCurrentBalance(),
            g.getCurrency(), g.isActive(), g.getExpiresAt(), g.getNote(),
            g.getPurchaserUserId(), g.getRecipientEmail(), g.getCreatedAt()
        );
    }
}
