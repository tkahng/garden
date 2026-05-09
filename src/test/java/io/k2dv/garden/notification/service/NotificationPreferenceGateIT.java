package io.k2dv.garden.notification.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.fulfillment.dto.CreateFulfillmentRequest;
import io.k2dv.garden.fulfillment.dto.FulfillmentItemRequest;
import io.k2dv.garden.fulfillment.dto.UpdateFulfillmentRequest;
import io.k2dv.garden.fulfillment.model.FulfillmentStatus;
import io.k2dv.garden.fulfillment.service.FulfillmentService;
import io.k2dv.garden.inventory.model.InventoryLevel;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.notification.dto.UpdateNotificationPreferencesRequest;
import io.k2dv.garden.notification.model.NotificationType;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderItemRepository;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.order.event.OrderCancelledEvent;
import io.k2dv.garden.order.event.OrderConfirmedEvent;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies that notification preference gates suppress or allow emails
 * in OrderService, FulfillmentService, and related services.
 */
@RecordApplicationEvents
class NotificationPreferenceGateIT extends AbstractIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired FulfillmentService fulfillmentService;
    @Autowired CartService cartService;
    @Autowired ProductService productService;
    @Autowired VariantService variantService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired OrderRepository orderRepo;
    @Autowired OrderItemRepository orderItemRepo;
    @Autowired LocationRepository locationRepo;
    @Autowired InventoryItemRepository inventoryItemRepo;
    @Autowired InventoryLevelRepository levelRepo;
    @Autowired NotificationPreferenceService preferenceService;
    @MockitoBean EmailService emailService;
    @Autowired ApplicationEvents applicationEvents;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private User user;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "gate-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Gate", "User"));
        user = userRepo.findByEmail(email).orElseThrow();
        userId = user.getId();

        AdminProductResponse product = productService.create(
            new CreateProductRequest("Widget", null, null, null, null, List.of(), null, null));
        productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        AdminVariantResponse variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("20.00"), null, null, null, null, null, List.of()));
        variantId = variant.id();

        Location location = new Location();
        location.setName("Loc-" + n);
        location = locationRepo.save(location);
        InventoryLevel level = new InventoryLevel();
        level.setInventoryItem(inventoryItemRepo.findByVariantId(variantId).orElseThrow());
        level.setLocation(location);
        level.setQuantityOnHand(20);
        levelRepo.save(level);

        cartService.getOrCreateActiveCart(userId);
    }

    private Order createAndPayOrder() {
        cartService.addItem(userId, new AddCartItemRequest(variantId, 1));
        Order order = orderService.createFromCart(userId,
            cartService.getCartItems(cartService.requireActiveCart(userId).getId()));
        String session = "fake-" + UUID.randomUUID();
        orderService.setStripeSession(order.getId(), session);
        orderService.confirmPayment(session, "pi-" + UUID.randomUUID());
        return orderRepo.findById(order.getId()).orElseThrow();
    }

    // ─── ORDER_CONFIRMATION gate ───────────────────────────────────────────────

    @Test
    void orderConfirmation_enabled_sendsCalled() {
        createAndPayOrder();
        assertThat(applicationEvents.stream(OrderConfirmedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void orderConfirmation_disabled_suppressesEmail() {
        preferenceService.updateForUser(userId, new UpdateNotificationPreferencesRequest(
            Map.of(NotificationType.ORDER_CONFIRMATION, false)));

        createAndPayOrder();

        assertThat(applicationEvents.stream(OrderConfirmedEvent.class).count()).isZero();
    }

    // ─── ORDER_CANCELLED gate ─────────────────────────────────────────────────

    @Test
    void orderCancelled_enabled_sendsCalled() {
        cartService.addItem(userId, new AddCartItemRequest(variantId, 1));
        Order order = orderService.createFromCart(userId,
            cartService.getCartItems(cartService.requireActiveCart(userId).getId()));
        orderService.cancelAndReturn(order.getId());
        assertThat(applicationEvents.stream(OrderCancelledEvent.class).count()).isEqualTo(1);
    }

    @Test
    void orderCancelled_disabled_suppressesEmail() {
        preferenceService.updateForUser(userId, new UpdateNotificationPreferencesRequest(
            Map.of(NotificationType.ORDER_CANCELLED, false)));

        cartService.addItem(userId, new AddCartItemRequest(variantId, 1));
        Order order = orderService.createFromCart(userId,
            cartService.getCartItems(cartService.requireActiveCart(userId).getId()));
        orderService.cancelAndReturn(order.getId());

        assertThat(applicationEvents.stream(OrderCancelledEvent.class).count()).isZero();
    }

    // ─── ORDER_SHIPPED gate ───────────────────────────────────────────────────

    @Test
    void orderShipped_enabled_sendsNotification() {
        Order order = createAndPayOrder();
        var item = orderItemRepo.findByOrderId(order.getId()).get(0);
        var f = fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest("TRK1", "UPS", null, null,
                List.of(new FulfillmentItemRequest(item.getId(), 1))), user);
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.SHIPPED, null, null, null, null));

        verify(emailService).sendShippingNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void orderShipped_disabled_suppressesEmail() {
        preferenceService.updateForUser(userId, new UpdateNotificationPreferencesRequest(
            Map.of(NotificationType.ORDER_SHIPPED, false)));

        Order order = createAndPayOrder();
        var item = orderItemRepo.findByOrderId(order.getId()).get(0);
        var f = fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest("TRK2", "UPS", null, null,
                List.of(new FulfillmentItemRequest(item.getId(), 1))), user);
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.SHIPPED, null, null, null, null));

        verify(emailService, never()).sendShippingNotification(any(), any(), any(), any(), any(), any());
    }

    // ─── ORDER_DELIVERED gate ─────────────────────────────────────────────────

    @Test
    void orderDelivered_enabled_sendsNotification() {
        Order order = createAndPayOrder();
        var item = orderItemRepo.findByOrderId(order.getId()).get(0);
        var f = fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest("TRK3", "FedEx", null, null,
                List.of(new FulfillmentItemRequest(item.getId(), 1))), user);
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.SHIPPED, null, null, null, null));
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.DELIVERED, null, null, null, null));

        verify(emailService).sendOrderDelivered(any(), any(), any(), any());
    }

    @Test
    void orderDelivered_disabled_suppressesEmail() {
        preferenceService.updateForUser(userId, new UpdateNotificationPreferencesRequest(
            Map.of(NotificationType.ORDER_DELIVERED, false)));

        Order order = createAndPayOrder();
        var item = orderItemRepo.findByOrderId(order.getId()).get(0);
        var f = fulfillmentService.create(order.getId(),
            new CreateFulfillmentRequest("TRK4", "FedEx", null, null,
                List.of(new FulfillmentItemRequest(item.getId(), 1))), user);
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.SHIPPED, null, null, null, null));
        fulfillmentService.update(order.getId(), f.id(),
            new UpdateFulfillmentRequest(FulfillmentStatus.DELIVERED, null, null, null, null));

        verify(emailService, never()).sendOrderDelivered(any(), any(), any(), any());
    }
}
