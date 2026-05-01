package io.k2dv.garden.order.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "checkout", name = "orders")
@Getter
@Setter
public class Order extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "shipping_cost", precision = 19, scale = 4)
    private BigDecimal shippingCost;

    @Column(name = "shipping_rate_id")
    private UUID shippingRateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String currency = "usd";

    @Column(name = "discount_id")
    private UUID discountId;

    @Column(name = "discount_amount", precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", columnDefinition = "jsonb")
    private String shippingAddress;

    @Column(name = "gift_card_id")
    private UUID giftCardId;

    @Column(name = "gift_card_amount", precision = 19, scale = 4)
    private BigDecimal giftCardAmount;

    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "po_number")
    private String poNumber;
}
