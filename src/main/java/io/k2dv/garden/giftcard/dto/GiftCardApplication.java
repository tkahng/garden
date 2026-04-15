package io.k2dv.garden.giftcard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GiftCardApplication(
    UUID giftCardId,
    String code,
    BigDecimal appliedAmount
) {}
