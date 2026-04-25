package io.k2dv.garden.inventory.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "inventory", name = "inventory_levels", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "inventory_item_id", "location_id" })
})
@Getter
@Setter
public class InventoryLevel extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inventory_item_id", nullable = false)
  private InventoryItem inventoryItem;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "location_id", nullable = false)
  private Location location;

  @Column(name = "quantity_on_hand", nullable = false)
  private int quantityOnHand = 0;

  @Column(name = "quantity_committed", nullable = false)
  private int quantityCommitted = 0;

  @Column(name = "low_stock_alerted_at")
  private java.time.Instant lowStockAlertedAt;
}
