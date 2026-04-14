package io.k2dv.garden.giftcard.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "checkout", name = "gift_cards")
@Getter
@Setter
public class GiftCard extends BaseEntity {

    @Column(nullable = false, length = 32)
    private String code;

    @Column(name = "initial_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialBalance;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance;

    @Column(nullable = false, length = 3)
    private String currency = "usd";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "purchaser_user_id")
    private UUID purchaserUserId;

    @Column(name = "recipient_email", length = 256)
    private String recipientEmail;
}
