package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.PriceListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceListEntryRepository extends JpaRepository<PriceListEntry, UUID> {

    List<PriceListEntry> findByPriceListIdOrderByMinQtyAsc(UUID priceListId);

    void deleteByPriceListIdAndVariantId(UUID priceListId, UUID variantId);

    @Query("""
        SELECT e FROM PriceListEntry e
        WHERE e.priceListId IN :listIds
          AND e.variantId = :variantId
          AND e.minQty <= :qty
        ORDER BY e.minQty DESC
        """)
    List<PriceListEntry> findCandidates(
        @Param("listIds") List<UUID> listIds,
        @Param("variantId") UUID variantId,
        @Param("qty") int qty
    );

    Optional<PriceListEntry> findByPriceListIdAndVariantIdAndMinQty(UUID priceListId, UUID variantId, int minQty);
}
