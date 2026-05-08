package io.k2dv.garden.b2b.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "b2b", name = "company_product_catalogs",
    uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "product_id"}))
@Getter
@Setter
public class CompanyProductCatalog extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;
}
