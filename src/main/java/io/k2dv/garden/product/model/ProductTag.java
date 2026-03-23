package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_tags")
@Getter
@Setter
public class ProductTag extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;
}
