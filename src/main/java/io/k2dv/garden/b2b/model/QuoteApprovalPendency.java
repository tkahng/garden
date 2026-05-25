package io.k2dv.garden.b2b.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "b2b", name = "quote_approval_pendencies")
@Getter
@Setter
public class QuoteApprovalPendency {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "action")
    private String action;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
