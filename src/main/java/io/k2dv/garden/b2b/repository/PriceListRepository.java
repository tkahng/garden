package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

    List<PriceList> findByCompanyIdOrderByPriorityDesc(UUID companyId);

    @Query("""
        SELECT pl FROM PriceList pl
        WHERE pl.companyId = :companyId
          AND (pl.startsAt IS NULL OR pl.startsAt <= :now)
          AND (pl.endsAt   IS NULL OR pl.endsAt   >  :now)
        ORDER BY pl.priority DESC
        """)
    List<PriceList> findActiveLists(@Param("companyId") UUID companyId, @Param("now") Instant now);
}
