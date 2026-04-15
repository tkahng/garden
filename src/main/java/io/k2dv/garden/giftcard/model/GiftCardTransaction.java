package io.k2dv.garden.giftcard.model;

import io.k2dv.garden.shared.model.ImmutableBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "checkout", name = "gift_card_transactions")
@Getter
@Setter
public class GiftCardTransaction extends ImmutableBaseEntity {

    @Column(name = "gift_card_id", nullable = false)
    private UUID giftCardId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal delta;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(columnDefinition = "text")
    private String note;
}
