package io.k2dv.garden.inventory.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(schema = "inventory", name = "inventory_items")
@Getter
@Setter
public class InventoryItem extends BaseEntity {
    @Column(name = "variant_id", nullable = false, unique = true)
    private UUID variantId;
    @Column(name = "requires_shipping", nullable = false)
    private boolean requiresShipping = true;
}
