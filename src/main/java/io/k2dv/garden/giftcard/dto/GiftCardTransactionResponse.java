package io.k2dv.garden.giftcard.dto;

import io.k2dv.garden.giftcard.model.GiftCardTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GiftCardTransactionResponse(
    UUID id,
    UUID giftCardId,
    BigDecimal delta,
    UUID orderId,
    String note,
    Instant createdAt
) {
    public static GiftCardTransactionResponse from(GiftCardTransaction t) {
        return new GiftCardTransactionResponse(
            t.getId(), t.getGiftCardId(), t.getDelta(),
            t.getOrderId(), t.getNote(), t.getCreatedAt()
        );
    }
}
