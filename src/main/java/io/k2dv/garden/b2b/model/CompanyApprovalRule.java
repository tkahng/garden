package io.k2dv.garden.b2b.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "b2b", name = "company_approval_rules")
@Getter
@Setter
public class CompanyApprovalRule extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "threshold_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal thresholdAmount;

    @Column(name = "required_role", nullable = false)
    private String requiredRole;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
