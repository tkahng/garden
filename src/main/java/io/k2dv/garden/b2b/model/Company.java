package io.k2dv.garden.b2b.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(schema = "b2b", name = "companies")
@Getter
@Setter
public class Company extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "tax_id")
    private String taxId;

    @Column
    private String phone;

    @Column(name = "billing_address_line1")
    private String billingAddressLine1;

    @Column(name = "billing_address_line2")
    private String billingAddressLine2;

    @Column(name = "billing_city")
    private String billingCity;

    @Column(name = "billing_state")
    private String billingState;

    @Column(name = "billing_postal_code")
    private String billingPostalCode;

    @Column(name = "billing_country")
    private String billingCountry;

    @Column(name = "tax_exempt", nullable = false)
    private boolean taxExempt = false;

    @Column(name = "sales_rep_user_id")
    private UUID salesRepUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();
}
