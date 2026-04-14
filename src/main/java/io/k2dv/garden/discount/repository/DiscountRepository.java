package io.k2dv.garden.discount.repository;

import io.k2dv.garden.discount.model.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DiscountRepository extends JpaRepository<Discount, UUID>, JpaSpecificationExecutor<Discount> {

    Optional<Discount> findByCodeIgnoreCase(String code);

    @Modifying
    @Query("""
        UPDATE Discount d SET d.usedCount = d.usedCount + 1
        WHERE d.id = :id AND (d.maxUses IS NULL OR d.usedCount < d.maxUses)
        """)
    int incrementUsedCount(@Param("id") UUID id);
}
