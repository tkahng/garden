package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
public class InventoryItem extends BaseEntity {
    @Column(name = "variant_id", nullable = false, unique = true)
    private UUID variantId;
    @Column(name = "requires_shipping", nullable = false)
    private boolean requiresShipping = true;
}
