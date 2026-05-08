package io.k2dv.garden.order.template.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.inventory.model.InventoryLevel;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.order.template.dto.CreateOrderTemplateRequest;
import io.k2dv.garden.order.template.dto.OrderTemplateItemInput;
import io.k2dv.garden.order.template.dto.OrderTemplateResponse;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
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

class OrderTemplateServiceIT extends AbstractIntegrationTest {

    @Autowired OrderTemplateService templateService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired ProductService productService;
    @Autowired VariantService variantService;
    @Autowired LocationRepository locationRepo;
    @Autowired InventoryItemRepository inventoryItemRepo;
    @Autowired InventoryLevelRepository levelRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);
    private UUID userId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "tmpl-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();

        AdminProductResponse product = productService.create(
            new CreateProductRequest("Widget", null, null, null, null, List.of(), null, null));
        productService.changeStatus(product.id(),
            new io.k2dv.garden.product.dto.ProductStatusRequest(ProductStatus.ACTIVE));
        AdminVariantResponse variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("25.00"), null, null, null, null, null, List.of()));
        variantId = variant.id();

        Location location = new Location();
        location.setName("WH-" + n);
        location = locationRepo.save(location);
        InventoryLevel level = new InventoryLevel();
        level.setInventoryItem(inventoryItemRepo.findByVariantId(variantId).orElseThrow());
        level.setLocation(location);
        level.setQuantityOnHand(50);
        levelRepo.save(level);
    }

    @Test
    void create_and_list_returnsTemplate() {
        OrderTemplateResponse created = templateService.create(userId,
            new CreateOrderTemplateRequest("Monthly supply",
                List.of(new OrderTemplateItemInput(variantId, 3))));

        assertThat(created.name()).isEqualTo("Monthly supply");
        assertThat(created.items()).hasSize(1);
        assertThat(created.items().get(0).quantity()).isEqualTo(3);

        var list = templateService.listForUser(userId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).name()).isEqualTo("Monthly supply");
    }

    @Test
    void getById_ownedTemplate_returnsWithItems() {
        OrderTemplateResponse created = templateService.create(userId,
            new CreateOrderTemplateRequest("Q1",
                List.of(new OrderTemplateItemInput(variantId, 10))));

        OrderTemplateResponse fetched = templateService.getById(userId, created.id());
        assertThat(fetched.items()).hasSize(1);
        assertThat(fetched.items().get(0).variantId()).isEqualTo(variantId);
    }

    @Test
    void getById_wrongUser_throws404() {
        OrderTemplateResponse created = templateService.create(userId,
            new CreateOrderTemplateRequest("Mine",
                List.of(new OrderTemplateItemInput(variantId, 1))));

        assertThatThrownBy(() -> templateService.getById(UUID.randomUUID(), created.id()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_removesTemplate() {
        OrderTemplateResponse created = templateService.create(userId,
            new CreateOrderTemplateRequest("Temp",
                List.of(new OrderTemplateItemInput(variantId, 2))));

        templateService.delete(userId, created.id());

        assertThat(templateService.listForUser(userId)).isEmpty();
    }

    @Test
    void loadToCart_populatesActiveCart() {
        OrderTemplateResponse created = templateService.create(userId,
            new CreateOrderTemplateRequest("Load me",
                List.of(new OrderTemplateItemInput(variantId, 4))));

        CartResponse cart = templateService.loadToCart(userId, created.id());

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(4);
    }
}
