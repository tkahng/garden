package io.k2dv.garden.quote.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.AddMemberRequest;
import io.k2dv.garden.b2b.dto.CompanyResponse;
import io.k2dv.garden.b2b.dto.CreateCompanyRequest;
import io.k2dv.garden.b2b.dto.CreateCreditAccountRequest;
import io.k2dv.garden.b2b.dto.CreatePriceListRequest;
import io.k2dv.garden.b2b.dto.UpsertPriceListEntryRequest;
import io.k2dv.garden.b2b.model.InvoiceStatus;
import io.k2dv.garden.b2b.service.PriceListService;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.b2b.service.CreditAccountService;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.inventory.model.InventoryLevel;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.payment.dto.CheckoutResponse;
import io.k2dv.garden.payment.service.PaymentService;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.quote.dto.*;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class QuoteServiceIT extends AbstractIntegrationTest {

    @Autowired QuoteService quoteService;
    @Autowired QuoteCartService quoteCartService;
    @Autowired CompanyService companyService;
    @Autowired CreditAccountService creditAccountService;
    @Autowired PriceListService priceListService;
    @Autowired ProductService productService;
    @Autowired VariantService variantService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired LocationRepository locationRepo;
    @Autowired InventoryItemRepository inventoryItemRepo;
    @Autowired InventoryLevelRepository levelRepo;
    @Autowired BlobObjectRepository blobRepo;
    @MockitoBean EmailService emailService;
    @MockitoBean StorageService storageService;
    @MockitoBean QuotePdfService pdfService;
    @MockitoBean PaymentService paymentService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private UUID companyId;
    private AdminVariantResponse variant;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "quote-svc-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();

        CompanyResponse company = companyService.create(userId,
            new CreateCompanyRequest("Test Co", null, null, null, null, null, null, null, null));
        companyId = company.id();

        AdminProductResponse product = productService.create(
            new CreateProductRequest("Widget", null, null, null, null, List.of(), null, null));
        productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        variant = variantService.create(product.id(),
            new CreateVariantRequest(null, null, null, null, null, null, List.of()));

        Location location = new Location();
        location.setName("Warehouse");
        location = locationRepo.save(location);
        InventoryLevel level = new InventoryLevel();
        level.setInventoryItem(inventoryItemRepo.findByVariantId(variant.id()).orElseThrow());
        level.setLocation(location);
        level.setQuantityOnHand(50);
        levelRepo.save(level);

        doNothing().when(emailService).sendQuoteSubmitted(any(), any());
        doNothing().when(emailService).sendQuoteNewRequest(any(), any());
        doNothing().when(emailService).sendQuotePdf(any(), any(), any());
        doNothing().when(storageService).store(any(), any(), any(), anyLong());
        doNothing().when(storageService).delete(any());
        when(pdfService.generate(any(), any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(paymentService.createCheckoutSessionFromQuote(any(), any(), any()))
            .thenReturn(new CheckoutResponse("https://checkout.stripe.com/pay/cs_test", UUID.randomUUID()));
    }

    private QuoteRequestResponse submitQuote() {
        quoteCartService.getOrCreateActiveCart(userId);
        quoteCartService.addItem(userId, new AddQuoteCartItemRequest(variant.id(), 2, null));
        return quoteService.submit(userId, new SubmitQuoteRequest(
            companyId, "123 Main St", null, "Springfield", null, "12345", "US", null, null));
    }

    private QuoteRequestResponse sendQuote(UUID quoteId) {
        return quoteService.send(quoteId,
            new SendQuoteRequest(Instant.now().plus(7, ChronoUnit.DAYS)));
    }

    @Test
    void submit_createsQuoteAndTransitionsCart() {
        QuoteRequestResponse quote = submitQuote();

        assertThat(quote.status()).isEqualTo(QuoteStatus.PENDING);
        assertThat(quote.companyId()).isEqualTo(companyId);
        assertThat(quote.items()).hasSize(1);
        assertThat(quote.items().get(0).unitPrice()).isNull();
    }

    @Test
    void submit_emptyCart_throwsValidation() {
        quoteCartService.getOrCreateActiveCart(userId);
        assertThatThrownBy(() -> quoteService.submit(userId, new SubmitQuoteRequest(
            companyId, "123 Main St", null, "Springfield", null, "12345", "US", null, null)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void listForUser_returnsOwnQuotes() {
        submitQuote();
        var result = quoteService.listForUser(userId, PageRequest.of(0, 20));
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void reject_fromSentStatus_succeeds() {
        QuoteRequestResponse quote = submitQuote();

        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("150.00")));

        QuoteRequestResponse sent = sendQuote(quote.id());
        assertThat(sent.status()).isEqualTo(QuoteStatus.SENT);

        QuoteRequestResponse rejected = quoteService.reject(quote.id(), userId);
        assertThat(rejected.status()).isEqualTo(QuoteStatus.REJECTED);
    }

    @Test
    void reject_notInSentStatus_throwsConflict() {
        QuoteRequestResponse quote = submitQuote();
        assertThatThrownBy(() -> quoteService.reject(quote.id(), userId))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_fromPending_succeeds() {
        QuoteRequestResponse quote = submitQuote();
        QuoteRequestResponse cancelled = quoteService.cancel(quote.id());
        assertThat(cancelled.status()).isEqualTo(QuoteStatus.CANCELLED);
    }

    @Test
    void cancel_alreadyCancelled_throwsConflict() {
        QuoteRequestResponse quote = submitQuote();
        quoteService.cancel(quote.id());
        assertThatThrownBy(() -> quoteService.cancel(quote.id()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void send_missingUnitPrice_throwsValidation() {
        QuoteRequestResponse quote = submitQuote();
        // items still have null unit price
        assertThatThrownBy(() -> sendQuote(quote.id()))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void send_allPricesSet_transitionsToSent() {
        QuoteRequestResponse quote = submitQuote();
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("150.00")));

        QuoteRequestResponse sent = sendQuote(quote.id());
        assertThat(sent.status()).isEqualTo(QuoteStatus.SENT);
        assertThat(sent.expiresAt()).isNotNull();
    }

    @Test
    void assign_transitions_toAssigned() {
        QuoteRequestResponse quote = submitQuote();
        int n = counter.incrementAndGet();
        String staffEmail = "staff-" + n + "@example.com";
        authService.register(new RegisterRequest(staffEmail, "pass", "Staff", "User"));
        UUID staffId = userRepo.findByEmail(staffEmail).orElseThrow().getId();

        QuoteRequestResponse assigned = quoteService.assign(quote.id(), new AssignStaffRequest(staffId));
        assertThat(assigned.status()).isEqualTo(QuoteStatus.ASSIGNED);
        assertThat(assigned.assignedStaffId()).isEqualTo(staffId);
    }

    @Test
    void addItem_byAdmin_addsCustomLineItem() {
        QuoteRequestResponse quote = submitQuote();
        QuoteItemResponse item = quoteService.addItem(quote.id(),
            new AddQuoteItemRequest("Custom freight charge", 1, new BigDecimal("75.00")));

        assertThat(item.description()).isEqualTo("Custom freight charge");
        assertThat(item.unitPrice()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void removeItem_byAdmin_removesItem() {
        QuoteRequestResponse quote = submitQuote();
        UUID itemId = quote.items().get(0).id();
        quoteService.removeItem(quote.id(), itemId);

        QuoteRequestResponse updated = quoteService.getAdmin(quote.id());
        assertThat(updated.items()).isEmpty();
    }

    @Test
    void accept_fromNonSentStatus_throwsConflict() {
        QuoteRequestResponse quote = submitQuote();
        assertThatThrownBy(() -> quoteService.accept(quote.id(), userId))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateNotes_setsStaffNotes() {
        QuoteRequestResponse quote = submitQuote();
        QuoteRequestResponse updated = quoteService.updateNotes(quote.id(),
            new UpdateStaffNotesRequest("Internal note for team"));
        assertThat(updated.staffNotes()).isEqualTo("Internal note for team");
    }

    @Test
    void cancelForUser_fromPending_succeeds() {
        QuoteRequestResponse quote = submitQuote();
        QuoteRequestResponse cancelled = quoteService.cancelForUser(quote.id(), userId);
        assertThat(cancelled.status()).isEqualTo(QuoteStatus.CANCELLED);
    }

    @Test
    void cancelForUser_wrongUser_throwsForbidden() {
        QuoteRequestResponse quote = submitQuote();
        assertThatThrownBy(() -> quoteService.cancelForUser(quote.id(), UUID.randomUUID()))
            .isInstanceOf(io.k2dv.garden.shared.exception.ForbiddenException.class);
    }

    @Test
    void cancelForUser_alreadyCancelled_throwsConflict() {
        QuoteRequestResponse quote = submitQuote();
        quoteService.cancelForUser(quote.id(), userId);
        assertThatThrownBy(() -> quoteService.cancelForUser(quote.id(), userId))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void accept_fromSentStatus_returnsCheckoutUrl() {
        QuoteRequestResponse quote = submitQuote();
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("150.00")));
        sendQuote(quote.id());

        QuoteAcceptResponse response = quoteService.accept(quote.id(), userId);

        assertThat(response.checkoutUrl()).isNotBlank();
        assertThat(response.orderId()).isNotNull();

        QuoteRequestResponse reloaded = quoteService.getAdmin(quote.id());
        assertThat(reloaded.status()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(reloaded.orderId()).isNotNull();
    }

    @Test
    void accept_expiredQuote_transitionsToExpiredAndThrows() {
        QuoteRequestResponse quote = submitQuote();
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("150.00")));
        // Send with already-past expiry
        quoteService.send(quote.id(), new SendQuoteRequest(Instant.now().minus(1, ChronoUnit.DAYS)));

        assertThatThrownBy(() -> quoteService.accept(quote.id(), userId))
            .isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("QUOTE_EXPIRED");

        assertThat(quoteService.getAdmin(quote.id()).status()).isEqualTo(QuoteStatus.EXPIRED);
    }

    @Test
    void getForUser_wrongOwner_throwsForbidden() {
        QuoteRequestResponse quote = submitQuote();
        assertThatThrownBy(() -> quoteService.getForUser(quote.id(), UUID.randomUUID()))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void submit_nonExistentCompany_throwsNotFound() {
        quoteCartService.getOrCreateActiveCart(userId);
        quoteCartService.addItem(userId, new AddQuoteCartItemRequest(variant.id(), 1, null));
        assertThatThrownBy(() -> quoteService.submit(userId, new SubmitQuoteRequest(
            UUID.randomUUID(), "123 Main", null, "City", null, "12345", "US", null, null)))
            .isInstanceOf(NotFoundException.class)
            .extracting("errorCode").isEqualTo("COMPANY_NOT_FOUND");
    }

    @Test
    void submit_nonMember_throwsForbidden() {
        int n = counter.incrementAndGet();
        String otherEmail = "other-" + n + "@example.com";
        authService.register(new RegisterRequest(otherEmail, "pass", "Other", "User"));
        UUID otherId = userRepo.findByEmail(otherEmail).orElseThrow().getId();

        quoteCartService.getOrCreateActiveCart(otherId);
        quoteCartService.addItem(otherId, new AddQuoteCartItemRequest(variant.id(), 1, null));
        assertThatThrownBy(() -> quoteService.submit(otherId, new SubmitQuoteRequest(
            companyId, "123 Main", null, "City", null, "12345", "US", null, null)))
            .isInstanceOf(ForbiddenException.class)
            .extracting("errorCode").isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void send_setsPdfBlobId() {
        QuoteRequestResponse quote = submitQuote();
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("150.00")));
        QuoteRequestResponse sent = sendQuote(quote.id());
        assertThat(sent.pdfBlobId()).isNotNull();
    }

    // --- Spending limit / approval chain ---

    private UUID createMemberWithLimit(BigDecimal limit) {
        int n = counter.incrementAndGet();
        String email = "member-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Member", "User"));
        UUID memberId = userRepo.findByEmail(email).orElseThrow().getId();
        companyService.addMember(companyId, userId, new AddMemberRequest(email, limit));
        return memberId;
    }

    private QuoteRequestResponse submitQuoteAs(UUID buyerId) {
        quoteCartService.getOrCreateActiveCart(buyerId);
        quoteCartService.addItem(buyerId, new AddQuoteCartItemRequest(variant.id(), 2, null));
        return quoteService.submit(buyerId, new SubmitQuoteRequest(
            companyId, "123 Main St", null, "Springfield", null, "12345", "US", null, null));
    }

    @Test
    void accept_belowSpendingLimit_returnsCheckoutUrlDirectly() {
        UUID memberId = createMemberWithLimit(new BigDecimal("10000.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("100.00"))); // total = 200, limit = 10000
        sendQuote(quote.id());

        QuoteAcceptResponse response = quoteService.accept(quote.id(), memberId);

        assertThat(response.pendingApproval()).isFalse();
        assertThat(response.checkoutUrl()).isNotBlank();
        assertThat(response.orderId()).isNotNull();
        assertThat(quoteService.getAdmin(quote.id()).status()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void accept_overSpendingLimit_movesPendingApproval() {
        UUID memberId = createMemberWithLimit(new BigDecimal("100.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00"))); // total = 1000, limit = 100
        sendQuote(quote.id());

        QuoteAcceptResponse response = quoteService.accept(quote.id(), memberId);

        assertThat(response.pendingApproval()).isTrue();
        assertThat(response.checkoutUrl()).isNull();
        assertThat(response.orderId()).isNull();
        assertThat(quoteService.getAdmin(quote.id()).status()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
    }

    @Test
    void accept_noSpendingLimit_alwaysApproved() {
        // userId is the OWNER — no spending limit set
        QuoteRequestResponse quote = submitQuote();
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("999999.00")));
        sendQuote(quote.id());

        QuoteAcceptResponse response = quoteService.accept(quote.id(), userId);

        assertThat(response.pendingApproval()).isFalse();
        assertThat(response.checkoutUrl()).isNotBlank();
    }

    @Test
    void approveSpend_byOwner_createsOrderAndReturnsCheckout() {
        UUID memberId = createMemberWithLimit(new BigDecimal("100.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), memberId); // → PENDING_APPROVAL

        QuoteAcceptResponse approved = quoteService.approveSpend(quote.id(), userId);

        assertThat(approved.pendingApproval()).isFalse();
        assertThat(approved.checkoutUrl()).isNotBlank();
        assertThat(approved.orderId()).isNotNull();

        QuoteRequestResponse reloaded = quoteService.getAdmin(quote.id());
        assertThat(reloaded.status()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(reloaded.approverId()).isEqualTo(userId);
        assertThat(reloaded.approvedAt()).isNotNull();
    }

    @Test
    void approveSpend_byNonOwner_throwsForbidden() {
        UUID memberId = createMemberWithLimit(new BigDecimal("100.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), memberId);

        assertThatThrownBy(() -> quoteService.approveSpend(quote.id(), memberId))
            .isInstanceOf(ForbiddenException.class)
            .extracting("errorCode").isEqualTo("INSUFFICIENT_COMPANY_ROLE");
    }

    @Test
    void approveSpend_wrongStatus_throwsConflict() {
        QuoteRequestResponse quote = submitQuote();
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("100.00")));
        sendQuote(quote.id());
        // quote is SENT, not PENDING_APPROVAL

        assertThatThrownBy(() -> quoteService.approveSpend(quote.id(), userId))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectSpend_byOwner_transitionsToRejected() {
        UUID memberId = createMemberWithLimit(new BigDecimal("100.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), memberId);

        QuoteRequestResponse rejected = quoteService.rejectSpend(quote.id(), userId);

        assertThat(rejected.status()).isEqualTo(QuoteStatus.REJECTED);
    }

    @Test
    void rejectSpend_byNonOwner_throwsForbidden() {
        UUID memberId = createMemberWithLimit(new BigDecimal("100.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), memberId);

        assertThatThrownBy(() -> quoteService.rejectSpend(quote.id(), memberId))
            .isInstanceOf(ForbiddenException.class)
            .extracting("errorCode").isEqualTo("INSUFFICIENT_COMPANY_ROLE");
    }

    // --- Net terms path ---

    private void createCreditAccount(BigDecimal limit) {
        creditAccountService.create(
            new CreateCreditAccountRequest(companyId, limit, 30, "USD"));
    }

    @Test
    void accept_withCreditAccount_createsInvoiceInsteadOfStripe() {
        createCreditAccount(new BigDecimal("50000.00"));

        QuoteRequestResponse quote = submitQuote();
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("150.00")));
        sendQuote(quote.id());

        QuoteAcceptResponse response = quoteService.accept(quote.id(), userId);

        assertThat(response.checkoutUrl()).isNull();
        assertThat(response.invoiceId()).isNotNull();
        assertThat(response.orderId()).isNotNull();
        assertThat(response.pendingApproval()).isFalse();
        assertThat(quoteService.getAdmin(quote.id()).status()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void accept_withCreditAccount_creditLimitExceeded_throwsConflict() {
        createCreditAccount(new BigDecimal("1.00")); // limit far below order total

        QuoteRequestResponse quote = submitQuote();
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("150.00"))); // total = 300
        sendQuote(quote.id());

        assertThatThrownBy(() -> quoteService.accept(quote.id(), userId))
            .isInstanceOf(io.k2dv.garden.shared.exception.ConflictException.class)
            .extracting("errorCode").isEqualTo("CREDIT_LIMIT_EXCEEDED");
    }

    @Test
    void approveSpend_withCreditAccount_createsInvoice() {
        createCreditAccount(new BigDecimal("50000.00"));

        UUID memberId = createMemberWithLimit(new BigDecimal("100.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00"))); // total 1000 > limit 100
        sendQuote(quote.id());
        quoteService.accept(quote.id(), memberId); // → PENDING_APPROVAL

        QuoteAcceptResponse approved = quoteService.approveSpend(quote.id(), userId);

        assertThat(approved.invoiceId()).isNotNull();
        assertThat(approved.checkoutUrl()).isNull();
        assertThat(approved.orderId()).isNotNull();
        assertThat(quoteService.getAdmin(quote.id()).status()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void accept_withCreditAccount_noCreditLimit_usesStripeWhenNoCreditAccount() {
        // no credit account → Stripe path still works
        QuoteRequestResponse quote = submitQuote();
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("150.00")));
        sendQuote(quote.id());

        QuoteAcceptResponse response = quoteService.accept(quote.id(), userId);

        assertThat(response.checkoutUrl()).isNotBlank();
        assertThat(response.invoiceId()).isNull();
    }

    // --- Manager role ---

    private UUID createMemberWithRole(io.k2dv.garden.b2b.model.CompanyRole role) {
        int n = counter.incrementAndGet();
        String email = "role-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Role", "User"));
        UUID memberId = userRepo.findByEmail(email).orElseThrow().getId();
        companyService.addMember(companyId, userId, new AddMemberRequest(email, new BigDecimal("100.00")));
        if (role != io.k2dv.garden.b2b.model.CompanyRole.MEMBER) {
            companyService.updateMemberRole(companyId, memberId, userId,
                new io.k2dv.garden.b2b.dto.UpdateMemberRoleRequest(role));
        }
        return memberId;
    }

    @Test
    void manager_canApproveSpend() {
        UUID managerId = createMemberWithRole(io.k2dv.garden.b2b.model.CompanyRole.MANAGER);
        UUID buyerId = createMemberWithLimit(new BigDecimal("50.00"));

        QuoteRequestResponse quote = submitQuoteAs(buyerId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), buyerId); // → PENDING_APPROVAL

        QuoteAcceptResponse approved = quoteService.approveSpend(quote.id(), managerId);

        assertThat(approved.pendingApproval()).isFalse();
        assertThat(approved.orderId()).isNotNull();
        assertThat(quoteService.getAdmin(quote.id()).status()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void manager_canRejectSpend() {
        UUID managerId = createMemberWithRole(io.k2dv.garden.b2b.model.CompanyRole.MANAGER);
        UUID buyerId = createMemberWithLimit(new BigDecimal("50.00"));

        QuoteRequestResponse quote = submitQuoteAs(buyerId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), buyerId);

        QuoteRequestResponse rejected = quoteService.rejectSpend(quote.id(), managerId);
        assertThat(rejected.status()).isEqualTo(QuoteStatus.REJECTED);
    }

    @Test
    void manager_seesListPendingApprovals() {
        UUID managerId = createMemberWithRole(io.k2dv.garden.b2b.model.CompanyRole.MANAGER);
        UUID buyerId = createMemberWithLimit(new BigDecimal("50.00"));

        QuoteRequestResponse quote = submitQuoteAs(buyerId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), buyerId);

        var result = quoteService.listPendingApprovals(managerId, PageRequest.of(0, 20));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(quote.id());
    }

    // --- Pending approvals list ---

    @Test
    void listPendingApprovals_ownerSeesTeamPendingQuotes() {
        UUID memberId = createMemberWithLimit(new BigDecimal("100.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), memberId); // → PENDING_APPROVAL

        var result = quoteService.listPendingApprovals(userId, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(quote.id());
        assertThat(result.getContent().get(0).status()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
    }

    @Test
    void listPendingApprovals_memberSeesNothing() {
        UUID memberId = createMemberWithLimit(new BigDecimal("100.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), memberId);

        // member is not an OWNER → empty list
        var result = quoteService.listPendingApprovals(memberId, PageRequest.of(0, 20));
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listPendingApprovals_afterApproval_quoteRemovedFromList() {
        UUID memberId = createMemberWithLimit(new BigDecimal("100.00"));

        QuoteRequestResponse quote = submitQuoteAs(memberId);
        quoteService.updateItem(quote.id(), quote.items().get(0).id(),
            new UpdateQuoteItemRequest(2, new BigDecimal("500.00")));
        sendQuote(quote.id());
        quoteService.accept(quote.id(), memberId);

        assertThat(quoteService.listPendingApprovals(userId, PageRequest.of(0, 20)).getContent()).hasSize(1);

        quoteService.approveSpend(quote.id(), userId);

        assertThat(quoteService.listPendingApprovals(userId, PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void submit_withContractPriceList_prePricedLineItems() {
        var pl = priceListService.create(
            new CreatePriceListRequest(companyId, "Contract", "USD", 10, null, null));
        priceListService.upsertEntry(pl.id(), variant.id(),
            new UpsertPriceListEntryRequest(new BigDecimal("7.50"), 1));

        QuoteRequestResponse quote = submitQuote();

        assertThat(quote.items()).hasSize(1);
        assertThat(quote.items().get(0).unitPrice()).isEqualByComparingTo("7.50");
    }

    @Test
    void submit_withoutPriceList_unitPriceIsNull() {
        QuoteRequestResponse quote = submitQuote();

        assertThat(quote.items()).hasSize(1);
        assertThat(quote.items().get(0).unitPrice()).isNull();
    }
}
