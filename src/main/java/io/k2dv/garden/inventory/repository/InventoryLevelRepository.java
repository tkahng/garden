package io.k2dv.garden.inventory.repository;

import io.k2dv.garden.inventory.model.InventoryLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {
    List<InventoryLevel> findByInventoryItemId(UUID inventoryItemId);
    Optional<InventoryLevel> findByInventoryItemIdAndLocationId(UUID inventoryItemId, UUID locationId);
}
