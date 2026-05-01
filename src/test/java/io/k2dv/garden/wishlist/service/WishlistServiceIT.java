package io.k2dv.garden.wishlist.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.repository.UserRepository;
import io.k2dv.garden.wishlist.dto.WishlistResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WishlistServiceIT extends AbstractIntegrationTest {

    @Autowired WishlistService wishlistService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired ProductService productService;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        authService.register(new RegisterRequest("wish-" + n + "@example.com", "password1", "Wish", "User"));
        userId = userRepo.findByEmail("wish-" + n + "@example.com").orElseThrow().getId();

        AdminProductResponse product = productService.create(
                new CreateProductRequest("Tulip Bulbs", null, null, null, null, List.of(), null, null));
        productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        productId = product.id();
    }

    private UUID activeProductId(String title) {
        AdminProductResponse p = productService.create(
                new CreateProductRequest(title, null, null, null, null, List.of(), null, null));
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        return p.id();
    }

    @Test
    void getWishlist_noExistingWishlist_returnsEmptyResponse() {
        WishlistResponse resp = wishlistService.getWishlist(userId);

        assertThat(resp.id()).isNull();
        assertThat(resp.items()).isEmpty();
    }

    @Test
    void addItem_createsWishlistOnFirstAdd() {
        WishlistResponse resp = wishlistService.addItem(userId, productId);

        assertThat(resp.id()).isNotNull();
        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).productId()).isEqualTo(productId);
    }

    @Test
    void addItem_secondProduct_appendsToExistingWishlist() {
        UUID product2Id = activeProductId("Sunflower Seeds");

        wishlistService.addItem(userId, productId);
        WishlistResponse resp = wishlistService.addItem(userId, product2Id);

        assertThat(resp.items()).hasSize(2);
    }

    @Test
    void addItem_duplicateProduct_throwsConflict() {
        wishlistService.addItem(userId, productId);

        assertThatThrownBy(() -> wishlistService.addItem(userId, productId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void addItem_draftProduct_throwsNotFound() {
        AdminProductResponse draft = productService.create(
                new CreateProductRequest("Draft Bulb", null, null, null, null, List.of(), null, null));

        assertThatThrownBy(() -> wishlistService.addItem(userId, draft.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addItem_unknownProduct_throwsNotFound() {
        assertThatThrownBy(() -> wishlistService.addItem(userId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeItem_removesExistingItem() {
        wishlistService.addItem(userId, productId);

        WishlistResponse resp = wishlistService.removeItem(userId, productId);

        assertThat(resp.items()).isEmpty();
    }

    @Test
    void removeItem_noWishlist_throwsNotFound() {
        assertThatThrownBy(() -> wishlistService.removeItem(userId, productId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeItem_itemNotInWishlist_silentlySucceeds() {
        wishlistService.addItem(userId, productId);
        UUID otherId = activeProductId("Other Plant");

        WishlistResponse resp = wishlistService.removeItem(userId, otherId);

        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).productId()).isEqualTo(productId);
    }
}
