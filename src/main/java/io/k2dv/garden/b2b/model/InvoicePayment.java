package io.k2dv.garden.b2b.model;

import io.k2dv.garden.shared.model.ImmutableBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "b2b", name = "invoice_payments")
@Getter
@Setter
public class InvoicePayment extends ImmutableBaseEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.STRIPE;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column
    private String notes;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;
}
