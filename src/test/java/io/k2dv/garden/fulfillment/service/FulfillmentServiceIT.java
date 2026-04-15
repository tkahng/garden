package io.k2dv.garden.fulfillment.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.fulfillment.dto.CreateFulfillmentRequest;
import io.k2dv.garden.fulfillment.dto.FulfillmentItemRequest;
import io.k2dv.garden.fulfillment.dto.FulfillmentResponse;
import io.k2dv.garden.fulfillment.dto.UpdateFulfillmentRequest;
import io.k2dv.garden.fulfillment.model.FulfillmentStatus;
import io.k2dv.garden.inventory.model.InventoryLevel;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderItemRepository;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.model.User;
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

class FulfillmentServiceIT extends AbstractIntegrationTest {

    @Autowired FulfillmentService fulfillmentService;
    @Autowired OrderService orderService;
    @Autowired OrderItemRepository orderItemRepo;
    @Autowired CartService cartService;
    @Autowired ProductService productService;
    @Autowired VariantService variantService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired LocationRepository locationRepo;
    @Autowired InventoryItemRepository inventoryItemRepo;
    @Autowired InventoryLevelRepository levelRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private User adminUser;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "fulfillment-it-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Admin", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();
        adminUser = userRepo.findByEmail(email).orElseThrow();

        AdminProductResponse product = productService.create(
            new CreateProductRequest("Plant", null, null, null, null, List.of()));
        productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        AdminVariantResponse variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("25.00"), null, null, null, null, null, List.of()));
        variantId = variant.id();

        Location location = new Location();
        location.setName("Warehouse-" + n);
        location = locationRepo.save(location);

        InventoryLevel level = new InventoryLevel();
        level.setInventoryItem(inventoryItemRepo.findByVariantId(variantId).orElseThrow());
        level.setLocation(location);
        level.setQuantityOnHand(20);
        levelRepo.save(level);

        cartService.getOrCreateActiveCart(userId);
    }

    /** Creates a PAID order with the given quantity of variantId. */
    private Order createPaidOrder(int quantity) {
        cartService.addItem(userId, new AddCartItemRequest(variantId, quantity));
        Order order = orderService.createFromCart(userId,
            cartService.getCartItems(cartService.requireActiveCart(userId).getId()));
        String fakeSession = "fake-session-" + UUID.randomUUID();
        orderService.setStripeSession(order.getId(), fakeSession);
        orderService.confirmPayment(fakeSession, "fake-intent-" + UUID.randomUUID());
        return orderService.getById(order.getId());
    }

    // ---- create: happy path ----

    @Test
    void create_paidOrder_succeeds() {
        Order order = createPaidOrder(3);
        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);

        FulfillmentResponse resp = fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest("TRACK1", "UPS", null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 2))),
            adminUser);

        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void create_fullyFulfilledOrder_statusBecomesFullfilled() {
        Order order = createPaidOrder(2);
        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);

        fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest(null, null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 2))),
            adminUser);

        Order updated = orderService.getById(order.getId());
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    void create_partialFulfillment_statusBecomesPartiallyFulfilled() {
        Order order = createPaidOrder(4);
        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);

        fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest(null, null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 2))),
            adminUser);

        Order updated = orderService.getById(order.getId());
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FULFILLED);
    }

    // ---- create: guards ----

    @Test
    void create_pendingPaymentOrder_throwsConflict() {
        cartService.addItem(userId, new AddCartItemRequest(variantId, 1));
        Order order = orderService.createFromCart(userId,
            cartService.getCartItems(cartService.requireActiveCart(userId).getId()));
        // Order is still PENDING_PAYMENT

        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);

        assertThatThrownBy(() -> fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest(null, null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 1))),
            adminUser))
            .isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("INVALID_ORDER_STATUS");
    }

    @Test
    void create_orderItemNotBelongingToOrder_throwsValidation() {
        Order order = createPaidOrder(2);
        UUID foreignItemId = UUID.randomUUID();

        assertThatThrownBy(() -> fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest(null, null, null, null,
                List.of(new FulfillmentItemRequest(foreignItemId, 1))),
            adminUser))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("ORDER_ITEM_NOT_FOUND");
    }

    @Test
    void create_overFulfillment_throwsConflict() {
        Order order = createPaidOrder(2);
        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);

        assertThatThrownBy(() -> fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest(null, null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 5))), // ordered only 2
            adminUser))
            .isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("OVER_FULFILLMENT");
    }

    @Test
    void create_cumulativeOverFulfillment_throwsConflict() {
        Order order = createPaidOrder(3);
        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);

        // First fulfillment: 2 of 3
        fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest(null, null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 2))),
            adminUser);

        // Second fulfillment tries to ship 2 more but only 1 remains
        assertThatThrownBy(() -> fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest(null, null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 2))),
            adminUser))
            .isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("OVER_FULFILLMENT");
    }

    // ---- update: status transitions ----

    @Test
    void update_pendingToShipped_succeeds() {
        Order order = createPaidOrder(2);
        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);
        FulfillmentResponse f = fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest("T1", null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 2))),
            adminUser);

        FulfillmentResponse updated = fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.SHIPPED, null, null, null, null));
        assertThat(updated.status()).isEqualTo(FulfillmentStatus.SHIPPED);
    }

    @Test
    void update_shippedToDelivered_succeeds() {
        Order order = createPaidOrder(2);
        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);
        FulfillmentResponse f = fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest("T2", null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 2))),
            adminUser);
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.SHIPPED, null, null, null, null));

        FulfillmentResponse delivered = fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.DELIVERED, null, null, null, null));
        assertThat(delivered.status()).isEqualTo(FulfillmentStatus.DELIVERED);
    }

    @Test
    void update_deliveredToPending_throwsConflict() {
        Order order = createPaidOrder(2);
        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);
        FulfillmentResponse f = fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest("T3", null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 2))),
            adminUser);
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.SHIPPED, null, null, null, null));
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.DELIVERED, null, null, null, null));

        assertThatThrownBy(() -> fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.PENDING, null, null, null, null)))
            .isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void update_cancelledToShipped_throwsConflict() {
        Order order = createPaidOrder(2);
        var orderItem = orderItemRepo.findByOrderId(order.getId()).get(0);
        FulfillmentResponse f = fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest("T4", null, null, null,
                List.of(new FulfillmentItemRequest(orderItem.getId(), 2))),
            adminUser);
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.CANCELLED, null, null, null, null));

        assertThatThrownBy(() -> fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.SHIPPED, null, null, null, null)))
            .isInstanceOf(ConflictException.class)
            .extracting("errorCode").isEqualTo("INVALID_STATUS_TRANSITION");
    }
}
