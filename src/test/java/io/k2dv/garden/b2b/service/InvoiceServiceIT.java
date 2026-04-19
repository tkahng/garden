package io.k2dv.garden.b2b.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.CreateCompanyRequest;
import io.k2dv.garden.b2b.dto.InvoiceResponse;
import io.k2dv.garden.b2b.dto.RecordPaymentRequest;
import io.k2dv.garden.b2b.model.InvoiceStatus;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceServiceIT extends AbstractIntegrationTest {

    @Autowired InvoiceService invoiceService;
    @Autowired CreditAccountService creditAccountService;
    @Autowired CompanyService companyService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired OrderRepository orderRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "invoice-test-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();
        companyId = companyService.create(userId,
            new CreateCompanyRequest("InvCo " + n, null, null, null, null, null, null, null, null)).id();
    }

    private Order savedOrder() {
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("500.00"));
        order.setCurrency("USD");
        return orderRepo.saveAndFlush(order);
    }

    @Test
    void createFromOrder_createsInvoiceAndMarksOrderInvoiced() {
        Order order = savedOrder();

        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null,
            new BigDecimal("500.00"), "USD", 30);

        assertThat(invoice.getCompanyId()).isEqualTo(companyId);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("500.00");
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getDueAt()).isAfter(invoice.getIssuedAt());

        Order refreshed = orderRepo.findById(order.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(OrderStatus.INVOICED);
    }

    @Test
    void recordPayment_partial_setsStatusPartial() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("500.00"), "USD", 30);

        InvoiceResponse resp = invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("200.00"), "REF-001", null, null));

        assertThat(resp.status()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(resp.paidAmount()).isEqualByComparingTo("200.00");
        assertThat(resp.outstandingAmount()).isEqualByComparingTo("300.00");
        assertThat(resp.payments()).hasSize(1);
    }

    @Test
    void recordPayment_full_setsStatusPaidAndOrderPaid() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("500.00"), "USD", 30);

        InvoiceResponse resp = invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("500.00"), "REF-FULL", null, null));

        assertThat(resp.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(resp.outstandingAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        Order refreshed = orderRepo.findById(order.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void recordPayment_twoPartials_thenFull() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("500.00"), "USD", 30);

        invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("200.00"), "P1", null, null));
        invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("200.00"), "P2", null, null));
        InvoiceResponse final_ = invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("100.00"), "P3", null, null));

        assertThat(final_.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(final_.payments()).hasSize(3);
    }

    @Test
    void recordPayment_overpayment_throwsValidation() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("500.00"), "USD", 30);

        assertThatThrownBy(() -> invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("600.00"), null, null, null)))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).getErrorCode()).isEqualTo("OVERPAYMENT"));
    }

    @Test
    void recordPayment_onPaidInvoice_throwsConflict() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("100.00"), "USD", 30);
        invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("100.00"), null, null, null));

        assertThatThrownBy(() -> invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("1.00"), null, null, null)))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> assertThat(((ConflictException) e).getErrorCode()).isEqualTo("INVOICE_NOT_PAYABLE"));
    }

    @Test
    void markOverdue_fromIssued_succeeds() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("500.00"), "USD", 30);

        InvoiceResponse resp = invoiceService.markOverdue(invoice.getId());
        assertThat(resp.status()).isEqualTo(InvoiceStatus.OVERDUE);
    }

    @Test
    void markOverdue_fromPartial_succeeds() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("500.00"), "USD", 30);
        invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("100.00"), null, null, null));

        InvoiceResponse resp = invoiceService.markOverdue(invoice.getId());
        assertThat(resp.status()).isEqualTo(InvoiceStatus.OVERDUE);
    }

    @Test
    void markOverdue_fromPaid_throwsConflict() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("100.00"), "USD", 30);
        invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("100.00"), null, null, null));

        assertThatThrownBy(() -> invoiceService.markOverdue(invoice.getId()))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> assertThat(((ConflictException) e).getErrorCode()).isEqualTo("INVALID_INVOICE_STATUS"));
    }

    @Test
    void voidInvoice_cancelsOrder() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("500.00"), "USD", 30);

        InvoiceResponse resp = invoiceService.voidInvoice(invoice.getId());
        assertThat(resp.status()).isEqualTo(InvoiceStatus.VOID);

        Order refreshed = orderRepo.findById(order.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void voidInvoice_paidInvoice_throwsConflict() {
        Order order = savedOrder();
        var invoice = invoiceService.createFromOrder(
            companyId, order.getId(), null, new BigDecimal("100.00"), "USD", 30);
        invoiceService.recordPayment(invoice.getId(),
            new RecordPaymentRequest(new BigDecimal("100.00"), null, null, null));

        assertThatThrownBy(() -> invoiceService.voidInvoice(invoice.getId()))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> assertThat(((ConflictException) e).getErrorCode()).isEqualTo("INVOICE_ALREADY_PAID"));
    }

    @Test
    void listByCompany_returnsInvoicesForCompany() {
        Order o1 = savedOrder();
        Order o2 = savedOrder();
        invoiceService.createFromOrder(companyId, o1.getId(), null, new BigDecimal("100.00"), "USD", 30);
        invoiceService.createFromOrder(companyId, o2.getId(), null, new BigDecimal("200.00"), "USD", 30);

        var list = invoiceService.listByCompany(companyId);
        assertThat(list).hasSize(2);
    }

    @Test
    void outstandingBalance_reflectedInCreditAccount() {
        io.k2dv.garden.b2b.dto.CreateCreditAccountRequest caReq =
            new io.k2dv.garden.b2b.dto.CreateCreditAccountRequest(
                companyId, new BigDecimal("1000.00"), null, null);
        creditAccountService.create(caReq);

        Order order = savedOrder();
        invoiceService.createFromOrder(companyId, order.getId(), null, new BigDecimal("300.00"), "USD", 30);

        var ca = creditAccountService.getByCompany(companyId);
        assertThat(ca.outstandingBalance()).isEqualByComparingTo("300.00");
        assertThat(ca.availableCredit()).isEqualByComparingTo("700.00");
    }
}
