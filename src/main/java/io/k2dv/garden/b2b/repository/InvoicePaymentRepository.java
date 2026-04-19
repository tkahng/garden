package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {
    List<InvoicePayment> findByInvoiceIdOrderByPaidAtAsc(UUID invoiceId);
}
