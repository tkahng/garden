package io.k2dv.garden.scheduler;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.CreateCompanyRequest;
import io.k2dv.garden.b2b.model.Invoice;
import io.k2dv.garden.b2b.model.InvoiceStatus;
import io.k2dv.garden.b2b.repository.InvoiceRepository;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

class ExpirySchedulerInvoiceIT extends AbstractIntegrationTest {

    @Autowired ExpiryScheduler scheduler;
    @Autowired InvoiceRepository invoiceRepo;
    @Autowired OrderRepository orderRepo;
    @Autowired AuthService authService;
    @Autowired CompanyService companyService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        doNothing().when(emailService).sendEmailVerification(any(), any());
        int n = counter.incrementAndGet();
        String email = "sched-inv-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Sched", "Inv"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();
        companyId = companyService.create(userId,
            new CreateCompanyRequest("SchedInvCo " + n, null, null, null, null, null, null, null, null)).id();
    }

    private Order savedOrder() {
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("500.00"));
        order.setCurrency("USD");
        return orderRepo.saveAndFlush(order);
    }

    private Invoice savedInvoice(InvoiceStatus status, Instant dueAt) {
        Invoice inv = new Invoice();
        inv.setCompanyId(companyId);
        inv.setOrderId(savedOrder().getId());
        inv.setStatus(status);
        inv.setTotalAmount(new BigDecimal("500.00"));
        inv.setPaidAmount(BigDecimal.ZERO);
        inv.setCurrency("USD");
        inv.setIssuedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        inv.setDueAt(dueAt);
        return invoiceRepo.save(inv);
    }

    @Test
    void markInvoicesOverdue_transitionsIssuedPastDue() {
        Invoice inv = savedInvoice(InvoiceStatus.ISSUED, Instant.now().minus(1, ChronoUnit.HOURS));

        scheduler.markInvoicesOverdue();

        assertThat(invoiceRepo.findById(inv.getId()).orElseThrow().getStatus())
            .isEqualTo(InvoiceStatus.OVERDUE);
    }

    @Test
    void markInvoicesOverdue_transitionsPartialPastDue() {
        Invoice inv = savedInvoice(InvoiceStatus.PARTIAL, Instant.now().minus(1, ChronoUnit.HOURS));

        scheduler.markInvoicesOverdue();

        assertThat(invoiceRepo.findById(inv.getId()).orElseThrow().getStatus())
            .isEqualTo(InvoiceStatus.OVERDUE);
    }

    @Test
    void markInvoicesOverdue_ignoresIssuedNotYetDue() {
        Invoice inv = savedInvoice(InvoiceStatus.ISSUED, Instant.now().plus(1, ChronoUnit.DAYS));

        scheduler.markInvoicesOverdue();

        assertThat(invoiceRepo.findById(inv.getId()).orElseThrow().getStatus())
            .isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void markInvoicesOverdue_doesNotTouchPaidOrVoid() {
        Invoice paid   = savedInvoice(InvoiceStatus.PAID, Instant.now().minus(1, ChronoUnit.HOURS));
        Invoice voided = savedInvoice(InvoiceStatus.VOID, Instant.now().minus(1, ChronoUnit.HOURS));

        scheduler.markInvoicesOverdue();

        assertThat(invoiceRepo.findById(paid.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoiceRepo.findById(voided.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.VOID);
    }

    @Test
    void markInvoicesOverdue_alreadyOverdueUntouched() {
        Invoice already = savedInvoice(InvoiceStatus.OVERDUE, Instant.now().minus(1, ChronoUnit.HOURS));

        scheduler.markInvoicesOverdue();

        assertThat(invoiceRepo.findById(already.getId()).orElseThrow().getStatus())
            .isEqualTo(InvoiceStatus.OVERDUE);
    }
}
