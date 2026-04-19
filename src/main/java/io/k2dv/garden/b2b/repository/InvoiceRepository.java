package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.Invoice;
import io.k2dv.garden.b2b.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    List<Invoice> findByCompanyIdOrderByDueAtAsc(UUID companyId);

    @Query("""
        SELECT COALESCE(SUM(i.totalAmount - i.paidAmount), 0)
        FROM Invoice i
        WHERE i.companyId = :companyId
          AND i.status IN ('ISSUED', 'PARTIAL', 'OVERDUE')
        """)
    BigDecimal computeOutstandingBalance(@Param("companyId") UUID companyId);
}
