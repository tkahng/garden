package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "product_options")
@Getter
@Setter
public class ProductOption extends BaseEntity {
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private int position;
}
