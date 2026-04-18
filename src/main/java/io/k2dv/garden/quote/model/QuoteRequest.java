package io.k2dv.garden.quote.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "quote", name = "quote_requests")
@Getter
@Setter
public class QuoteRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "assigned_staff_id")
    private UUID assignedStaffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status = QuoteStatus.PENDING;

    @Column(name = "delivery_address_line1", nullable = false)
    private String deliveryAddressLine1;

    @Column(name = "delivery_address_line2")
    private String deliveryAddressLine2;

    @Column(name = "delivery_city", nullable = false)
    private String deliveryCity;

    @Column(name = "delivery_state")
    private String deliveryState;

    @Column(name = "delivery_postal_code", nullable = false)
    private String deliveryPostalCode;

    @Column(name = "delivery_country", nullable = false)
    private String deliveryCountry;

    @Column(name = "shipping_requirements")
    private String shippingRequirements;

    @Column(name = "customer_notes")
    private String customerNotes;

    @Column(name = "staff_notes")
    private String staffNotes;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "pdf_blob_id")
    private UUID pdfBlobId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "approver_id")
    private UUID approverId;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
