package io.k2dv.garden.b2b.service;

import io.k2dv.garden.b2b.dto.InvoicePaymentResponse;
import io.k2dv.garden.b2b.dto.InvoiceResponse;
import io.k2dv.garden.b2b.dto.RecordPaymentRequest;
import io.k2dv.garden.b2b.model.Invoice;
import io.k2dv.garden.b2b.model.InvoicePayment;
import io.k2dv.garden.b2b.model.InvoiceStatus;
import io.k2dv.garden.b2b.repository.InvoicePaymentRepository;
import io.k2dv.garden.b2b.repository.InvoiceRepository;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final InvoicePaymentRepository paymentRepo;
    private final OrderRepository orderRepo;

    @Transactional
    public Invoice createFromOrder(UUID companyId, UUID orderId, UUID quoteId,
                                   BigDecimal total, String currency, int paymentTermsDays) {
        Instant now = Instant.now();
        Invoice invoice = new Invoice();
        invoice.setCompanyId(companyId);
        invoice.setOrderId(orderId);
        invoice.setQuoteId(quoteId);
        invoice.setTotalAmount(total);
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setCurrency(currency);
        invoice.setIssuedAt(now);
        invoice.setDueAt(now.plus(paymentTermsDays, ChronoUnit.DAYS));
        invoice = invoiceRepo.save(invoice);

        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        order.setStatus(OrderStatus.INVOICED);
        orderRepo.save(order);

        return invoice;
    }

    @Transactional
    public InvoiceResponse recordPayment(UUID invoiceId, RecordPaymentRequest req) {
        Invoice invoice = requireInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ConflictException("INVOICE_NOT_PAYABLE",
                "Cannot record payment on a " + invoice.getStatus() + " invoice");
        }

        BigDecimal remaining = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
        if (req.amount().compareTo(remaining) > 0) {
            throw new ValidationException("OVERPAYMENT",
                "Payment amount exceeds outstanding balance of " + remaining);
        }

        InvoicePayment payment = new InvoicePayment();
        payment.setInvoiceId(invoiceId);
        payment.setAmount(req.amount());
        payment.setPaymentReference(req.paymentReference());
        payment.setNotes(req.notes());
        payment.setPaidAt(req.paidAt() != null ? req.paidAt() : Instant.now());
        paymentRepo.save(payment);

        invoice.setPaidAmount(invoice.getPaidAmount().add(req.amount()));
        boolean fullyPaid = invoice.getPaidAmount().compareTo(invoice.getTotalAmount()) >= 0;
        invoice.setStatus(fullyPaid ? InvoiceStatus.PAID : InvoiceStatus.PARTIAL);
        invoiceRepo.save(invoice);

        if (fullyPaid) {
            orderRepo.findById(invoice.getOrderId()).ifPresent(order -> {
                order.setStatus(OrderStatus.PAID);
                orderRepo.save(order);
            });
        }

        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse markOverdue(UUID invoiceId) {
        Invoice invoice = requireInvoice(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PARTIAL) {
            throw new ConflictException("INVALID_INVOICE_STATUS",
                "Can only mark ISSUED or PARTIAL invoices as overdue");
        }
        invoice.setStatus(InvoiceStatus.OVERDUE);
        return toResponse(invoiceRepo.save(invoice));
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID invoiceId) {
        Invoice invoice = requireInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("INVOICE_ALREADY_PAID", "Cannot void a paid invoice");
        }
        invoice.setStatus(InvoiceStatus.VOID);
        invoiceRepo.save(invoice);

        Order order = orderRepo.findById(invoice.getOrderId()).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.INVOICED) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepo.save(order);
        }
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(UUID invoiceId) {
        return toResponse(requireInvoice(invoiceId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listByCompany(UUID companyId) {
        return invoiceRepo.findByCompanyIdOrderByDueAtAsc(companyId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PagedResult<InvoiceResponse> listAll(UUID companyId, InvoiceStatus status, Pageable pageable) {
        Specification<Invoice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (companyId != null) predicates.add(cb.equal(root.get("companyId"), companyId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PagedResult.of(invoiceRepo.findAll(spec, pageable), this::toResponse);
    }

    private Invoice requireInvoice(UUID id) {
        return invoiceRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found"));
    }

    private InvoiceResponse toResponse(Invoice inv) {
        List<InvoicePaymentResponse> payments = paymentRepo
            .findByInvoiceIdOrderByPaidAtAsc(inv.getId())
            .stream().map(this::toPaymentResponse).toList();
        BigDecimal outstanding = inv.getTotalAmount().subtract(inv.getPaidAmount());
        return new InvoiceResponse(
            inv.getId(), inv.getCompanyId(), inv.getOrderId(), inv.getQuoteId(),
            inv.getStatus(), inv.getTotalAmount(), inv.getPaidAmount(), outstanding,
            inv.getCurrency(), inv.getIssuedAt(), inv.getDueAt(),
            payments, inv.getCreatedAt(), inv.getUpdatedAt()
        );
    }

    private InvoicePaymentResponse toPaymentResponse(InvoicePayment p) {
        return new InvoicePaymentResponse(
            p.getId(), p.getInvoiceId(), p.getAmount(),
            p.getPaymentReference(), p.getNotes(), p.getPaidAt(), p.getCreatedAt()
        );
    }
}
