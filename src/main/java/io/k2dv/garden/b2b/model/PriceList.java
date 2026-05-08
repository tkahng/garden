package io.k2dv.garden.b2b.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "b2b", name = "price_lists")
@Getter
@Setter
public class PriceList extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(nullable = false)
    private int priority = 0;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type")
    private PriceListAdjustmentType adjustmentType;

    @Column(name = "adjustment_value", precision = 8, scale = 4)
    private BigDecimal adjustmentValue;
}
