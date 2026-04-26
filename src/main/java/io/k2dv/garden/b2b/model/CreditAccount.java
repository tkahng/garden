package io.k2dv.garden.b2b.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "b2b", name = "credit_accounts")
@Getter
@Setter
public class CreditAccount extends BaseEntity {

    @Column(name = "company_id", nullable = false, unique = true)
    private UUID companyId;

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 30;

    @Column(nullable = false)
    private String currency = "USD";
}
