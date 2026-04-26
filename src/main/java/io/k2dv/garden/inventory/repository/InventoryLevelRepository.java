package io.k2dv.garden.inventory.repository;

import io.k2dv.garden.inventory.model.InventoryLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {
    List<InventoryLevel> findByInventoryItemId(UUID inventoryItemId);
    Optional<InventoryLevel> findByInventoryItemIdAndLocationId(UUID inventoryItemId, UUID locationId);

    @Query(value = """
        SELECT il.* FROM inventory.inventory_levels il
        WHERE il.quantity_on_hand - il.quantity_committed <= :threshold
        AND il.quantity_on_hand - il.quantity_committed >= 0
        AND (il.low_stock_alerted_at IS NULL OR il.low_stock_alerted_at < :alertCutoff)
        """, nativeQuery = true)
    List<InventoryLevel> findLowStock(@Param("threshold") int threshold,
                                      @Param("alertCutoff") Instant alertCutoff);
}
