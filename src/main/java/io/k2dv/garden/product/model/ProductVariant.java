package io.k2dv.garden.product.model;

import io.k2dv.garden.inventory.model.FulfillmentType;
import io.k2dv.garden.inventory.model.InventoryPolicy;
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
@Table(schema = "catalog", name = "product_variants")
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
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false)
    private FulfillmentType fulfillmentType = FulfillmentType.IN_STOCK;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_policy", nullable = false)
    private InventoryPolicy inventoryPolicy = InventoryPolicy.DENY;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays = 0;
    @Column(precision = 19, scale = 4)
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
        schema = "catalog",
        name = "variant_option_values",
        joinColumns = @JoinColumn(name = "variant_id"),
        inverseJoinColumns = @JoinColumn(name = "option_value_id")
    )
    private List<ProductOptionValue> optionValues = new ArrayList<>();
}
