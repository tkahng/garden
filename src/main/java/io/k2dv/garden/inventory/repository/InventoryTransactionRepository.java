package io.k2dv.garden.inventory.repository;

import io.k2dv.garden.inventory.model.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
    Page<InventoryTransaction> findByInventoryItemIdAndLocationId(UUID inventoryItemId, UUID locationId, Pageable pageable);
    Page<InventoryTransaction> findByInventoryItemId(UUID inventoryItemId, Pageable pageable);
}
