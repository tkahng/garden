package io.k2dv.garden.discount.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(schema = "checkout", name = "discounts")
@Getter
@Setter
public class Discount extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Column(name = "min_order_amount", precision = 19, scale = 4)
    private BigDecimal minOrderAmount;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "company_id")
    private java.util.UUID companyId;
}
