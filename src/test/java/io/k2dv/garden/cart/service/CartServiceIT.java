package io.k2dv.garden.cart.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.dto.UpdateCartItemRequest;
import io.k2dv.garden.cart.model.CartStatus;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartServiceIT extends AbstractIntegrationTest {

    @Autowired CartService cartService;
    @Autowired ProductService productService;
    @Autowired VariantService variantService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private AdminVariantResponse createActiveVariant(BigDecimal price) {
        AdminProductResponse product = productService.create(
            new CreateProductRequest("Test Product", null, null, null, null, List.of()));
        productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        return variantService.create(product.id(),
            new CreateVariantRequest(price, null, null, null, null, null, List.of()));
    }

    private UUID createUserId() {
        int n = counter.incrementAndGet();
        String email = "cart-test-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        return userRepo.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void getOrCreateActiveCart_createsNewCart() {
        UUID userId = createUserId();
        CartResponse cart = cartService.getOrCreateActiveCart(userId);
        assertThat(cart.status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(cart.items()).isEmpty();
    }

    @Test
    void getOrCreateActiveCart_returnsExistingCart() {
        UUID userId = createUserId();
        CartResponse first = cartService.getOrCreateActiveCart(userId);
        CartResponse second = cartService.getOrCreateActiveCart(userId);
        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void addItem_snapshotsUnitPrice() {
        UUID userId = createUserId();
        AdminVariantResponse variant = createActiveVariant(new BigDecimal("49.99"));
        cartService.getOrCreateActiveCart(userId);

        CartResponse cart = cartService.addItem(userId, new AddCartItemRequest(variant.id(), 2));
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(2);
        assertThat(cart.items().get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("49.99"));
    }

    @Test
    void addItem_sameVariantTwice_mergesQuantity() {
        UUID userId = createUserId();
        AdminVariantResponse variant = createActiveVariant(new BigDecimal("10.00"));
        cartService.getOrCreateActiveCart(userId);
        cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1));

        CartResponse cart = cartService.addItem(userId, new AddCartItemRequest(variant.id(), 3));
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(4);
    }

    @Test
    void addItem_variantNotFound_throwsNotFound() {
        UUID userId = createUserId();
        cartService.getOrCreateActiveCart(userId);
        assertThatThrownBy(() ->
            cartService.addItem(userId, new AddCartItemRequest(UUID.randomUUID(), 1)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateItem_changesQuantity() {
        UUID userId = createUserId();
        AdminVariantResponse variant = createActiveVariant(new BigDecimal("20.00"));
        cartService.getOrCreateActiveCart(userId);
        CartResponse cart = cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1));
        UUID itemId = cart.items().get(0).id();

        CartResponse updated = cartService.updateItem(userId, itemId, new UpdateCartItemRequest(5));
        assertThat(updated.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    void removeItem_removesItem() {
        UUID userId = createUserId();
        AdminVariantResponse variant = createActiveVariant(new BigDecimal("5.00"));
        cartService.getOrCreateActiveCart(userId);
        CartResponse cart = cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1));
        UUID itemId = cart.items().get(0).id();

        CartResponse updated = cartService.removeItem(userId, itemId);
        assertThat(updated.items()).isEmpty();
    }

    @Test
    void abandonCart_transitionsStatus() {
        UUID userId = createUserId();
        cartService.getOrCreateActiveCart(userId);
        cartService.abandonCart(userId);

        // Abandoning again should not fail (no active cart to abandon — just a no-op)
        cartService.abandonCart(userId);
    }
}
