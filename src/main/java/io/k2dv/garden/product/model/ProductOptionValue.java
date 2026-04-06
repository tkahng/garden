package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "product_option_values")
@Getter
@Setter
public class ProductOptionValue extends BaseEntity {
    @Column(name = "option_id", nullable = false)
    private UUID optionId;
    @Column(nullable = false)
    private String label;
    @Column(nullable = false)
    private int position;
}
