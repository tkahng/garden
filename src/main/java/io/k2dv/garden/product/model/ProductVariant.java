package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
public class ProductVariant extends BaseEntity {
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(nullable = false)
    private String title;
    @Column(unique = true)
    private String sku;
    private String barcode;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;
    @Column(name = "compare_at_price", precision = 19, scale = 4)
    private BigDecimal compareAtPrice;
    @Column(precision = 10, scale = 4)
    private BigDecimal weight;
    @Column(name = "weight_unit")
    private String weightUnit;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "variant_option_values",
        joinColumns = @JoinColumn(name = "variant_id"),
        inverseJoinColumns = @JoinColumn(name = "option_value_id")
    )
    private List<ProductOptionValue> optionValues = new ArrayList<>();
}
