package io.k2dv.garden.inventory.repository;

import io.k2dv.garden.inventory.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByVariantId(UUID variantId);
    List<InventoryItem> findByVariantIdIn(List<UUID> variantIds);
}
