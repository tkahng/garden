package io.k2dv.garden.b2b.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "b2b", name = "company_shipping_addresses")
@Getter
@Setter
public class CompanyShippingAddress extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column
    private String label;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column
    private String company;

    @Column(name = "address1", nullable = false)
    private String address1;

    @Column(name = "address2")
    private String address2;

    @Column(nullable = false)
    private String city;

    @Column
    private String province;

    @Column(nullable = false)
    private String zip;

    @Column(nullable = false)
    private String country;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;
}
