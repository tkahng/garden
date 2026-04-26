package io.k2dv.garden.quote.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.quote.dto.AddQuoteCartItemRequest;
import io.k2dv.garden.quote.dto.QuoteCartResponse;
import io.k2dv.garden.quote.dto.UpdateQuoteCartItemRequest;
import io.k2dv.garden.quote.model.QuoteCartStatus;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteCartServiceIT extends AbstractIntegrationTest {

    @Autowired
    QuoteCartService quoteCartService;
    @Autowired
    ProductService productService;
    @Autowired
    VariantService variantService;
    @Autowired
    AuthService authService;
    @Autowired
    UserRepository userRepo;
    @MockitoBean
    EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);
    private UUID userId;
    private AdminVariantResponse variant;
    private AdminVariantResponse quoteOnlyVariant;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "qcart-test-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();

        AdminProductResponse product = productService.create(
            new CreateProductRequest("Test Product", null, null, null, null, List.of(), null, null));
        productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("100.00"), null, null, null, null, null, List.of()));

        // Quote-only variant (null price)
        quoteOnlyVariant = variantService.create(product.id(),
            new CreateVariantRequest(null, null, null, null, null, null, List.of()));
    }

    @Test
    void getOrCreateActiveCart_createsNewCart() {
        QuoteCartResponse cart = quoteCartService.getOrCreateActiveCart(userId);
        assertThat(cart.status()).isEqualTo(QuoteCartStatus.ACTIVE);
        assertThat(cart.items()).isEmpty();
    }

    @Test
    void getOrCreateActiveCart_returnsExisting() {
        QuoteCartResponse first = quoteCartService.getOrCreateActiveCart(userId);
        QuoteCartResponse second = quoteCartService.getOrCreateActiveCart(userId);
        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void addItem_withRegularVariant_succeeds() {
        quoteCartService.getOrCreateActiveCart(userId);
        QuoteCartResponse cart = quoteCartService.addItem(userId,
            new AddQuoteCartItemRequest(variant.id(), 2, "Please deliver fast"));
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(2);
        assertThat(cart.items().get(0).note()).isEqualTo("Please deliver fast");
    }

    @Test
    void addItem_withQuoteOnlyVariant_succeeds() {
        quoteCartService.getOrCreateActiveCart(userId);
        QuoteCartResponse cart = quoteCartService.addItem(userId,
            new AddQuoteCartItemRequest(quoteOnlyVariant.id(), 1, null));
        assertThat(cart.items()).hasSize(1);
    }

    @Test
    void addItem_sameVariantTwice_mergesQuantity() {
        quoteCartService.getOrCreateActiveCart(userId);
        quoteCartService.addItem(userId, new AddQuoteCartItemRequest(variant.id(), 1, null));
        QuoteCartResponse cart = quoteCartService.addItem(userId,
            new AddQuoteCartItemRequest(variant.id(), 3, null));
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(4);
    }

    @Test
    void addItem_unknownVariant_throwsNotFound() {
        quoteCartService.getOrCreateActiveCart(userId);
        assertThatThrownBy(() -> quoteCartService.addItem(userId,
            new AddQuoteCartItemRequest(UUID.randomUUID(), 1, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateItem_changesQuantityAndNote() {
        quoteCartService.getOrCreateActiveCart(userId);
        QuoteCartResponse cart = quoteCartService.addItem(userId,
            new AddQuoteCartItemRequest(variant.id(), 1, "original"));
        UUID itemId = cart.items().get(0).id();

        QuoteCartResponse updated = quoteCartService.updateItem(userId, itemId,
            new UpdateQuoteCartItemRequest(5, "updated note"));
        assertThat(updated.items().get(0).quantity()).isEqualTo(5);
        assertThat(updated.items().get(0).note()).isEqualTo("updated note");
    }

    @Test
    void removeItem_removesItem() {
        quoteCartService.getOrCreateActiveCart(userId);
        QuoteCartResponse cart = quoteCartService.addItem(userId,
            new AddQuoteCartItemRequest(variant.id(), 1, null));
        UUID itemId = cart.items().get(0).id();

        QuoteCartResponse updated = quoteCartService.removeItem(userId, itemId);
        assertThat(updated.items()).isEmpty();
    }

    @Test
    void clearCart_removesAllItems() {
        quoteCartService.getOrCreateActiveCart(userId);
        quoteCartService.addItem(userId, new AddQuoteCartItemRequest(variant.id(), 1, null));
        quoteCartService.clearCart(userId);

        QuoteCartResponse cart = quoteCartService.getOrCreateActiveCart(userId);
        assertThat(cart.items()).isEmpty();
    }

    @Test
    void addItem_noActiveCart_throwsValidation() {
        assertThatThrownBy(() -> quoteCartService.addItem(userId,
            new AddQuoteCartItemRequest(variant.id(), 1, null)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void markSubmitted_preventsNewActiveCart_createsNew() {
        QuoteCartResponse cart = quoteCartService.getOrCreateActiveCart(userId);
        quoteCartService.markSubmitted(cart.id());

        // After submission, getOrCreate creates a NEW cart
        QuoteCartResponse newCart = quoteCartService.getOrCreateActiveCart(userId);
        assertThat(newCart.id()).isNotEqualTo(cart.id());
        assertThat(newCart.status()).isEqualTo(QuoteCartStatus.ACTIVE);
    }
}
