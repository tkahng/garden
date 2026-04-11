package io.k2dv.garden.quote.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.CompanyResponse;
import io.k2dv.garden.b2b.dto.CreateCompanyRequest;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.inventory.model.InventoryLevel;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.quote.dto.*;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
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
            new CreateProductRequest("Widget", null, null, null, null, List.of()));
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
        doNothing().when(emailService).sendQuotePdf(any(), any(), any());
        doNothing().when(storageService).store(any(), any(), any(), anyLong());
        doNothing().when(storageService).delete(any());
        when(pdfService.generate(any(), any(), any())).thenReturn(new byte[]{1, 2, 3});
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
}
