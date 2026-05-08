package io.k2dv.garden.discount.repository;

import io.k2dv.garden.discount.model.Discount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountRepository extends JpaRepository<Discount, UUID>, JpaSpecificationExecutor<Discount> {

    Optional<Discount> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Discount d WHERE LOWER(d.code) = LOWER(:code)")
    Optional<Discount> findByCodeIgnoreCaseForUpdate(@Param("code") String code);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Discount d SET d.usedCount = d.usedCount + 1
        WHERE d.id = :id AND (d.maxUses IS NULL OR d.usedCount < d.maxUses)
        """)
    int incrementUsedCount(@Param("id") UUID id);

    @Query("""
        SELECT d FROM Discount d
        WHERE d.automatic = true AND d.isActive = true
          AND (d.startsAt IS NULL OR d.startsAt <= :now)
          AND (d.endsAt IS NULL OR d.endsAt >= :now)
          AND (d.maxUses IS NULL OR d.usedCount < d.maxUses)
          AND (d.companyId IS NULL OR d.companyId = :companyId)
        """)
    List<Discount> findActiveAutomatic(@Param("now") Instant now, @Param("companyId") UUID companyId);
}
