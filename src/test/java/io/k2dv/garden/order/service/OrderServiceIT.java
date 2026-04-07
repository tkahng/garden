package io.k2dv.garden.order.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.inventory.model.InventoryLevel;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderItemRepository;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
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

class OrderServiceIT extends AbstractIntegrationTest {

  @Autowired
  OrderService orderService;
  @Autowired
  ProductService productService;
  @Autowired
  VariantService variantService;
  @Autowired
  LocationRepository locationRepo;
  @Autowired
  InventoryItemRepository inventoryItemRepo;
  @Autowired
  InventoryLevelRepository levelRepo;
  @Autowired
  OrderItemRepository orderItemRepo;
  @Autowired
  AuthService authService;
  @Autowired
  UserRepository userRepo;
  @MockitoBean
  EmailService emailService;

  private static final AtomicInteger counter = new AtomicInteger(0);

  private AdminVariantResponse variant;
  private Location location;

  @BeforeEach
  void setUp() {
    AdminProductResponse product = productService.create(
        new CreateProductRequest("Test Product", null, null, null, null, List.of()));
    productService.changeStatus(product.id(),
        new io.k2dv.garden.product.dto.ProductStatusRequest(ProductStatus.ACTIVE));
    variant = variantService.create(product.id(),
        new CreateVariantRequest(new BigDecimal("50.00"), null, null, null, null, null, List.of()));

    location = new Location();
    location.setName("Main Warehouse");
    location = locationRepo.save(location);

    // Stock the variant at the location
    InventoryLevel level = new InventoryLevel();
    level.setInventoryItem(inventoryItemRepo.findByVariantId(variant.id()).orElseThrow());
    level.setLocation(location);
    level.setQuantityOnHand(10);
    levelRepo.save(level);
  }

  private UUID createUserId() {
    int n = counter.incrementAndGet();
    String email = "order-test-" + n + "-" + UUID.randomUUID() + "@example.com";
    authService.register(new RegisterRequest(email, "password1", "Test", "User"));
    return userRepo.findByEmail(email).orElseThrow().getId();
  }

  private CartItem cartItem(UUID variantId, int qty, BigDecimal price) {
    CartItem item = new CartItem();
    item.setVariantId(variantId);
    item.setQuantity(qty);
    item.setUnitPrice(price);
    return item;
  }

  @Test
  void createFromCart_createsOrderAndReservesInventory() {
    UUID userId = createUserId();
    List<CartItem> items = List.of(cartItem(variant.id(), 3, new BigDecimal("50.00")));

    Order order = orderService.createFromCart(userId, items);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("150.00"));

    InventoryLevel level = levelRepo.findByInventoryItemIdAndLocationId(
        inventoryItemRepo.findByVariantId(variant.id()).orElseThrow().getId(),
        location.getId()).orElseThrow();
    assertThat(level.getQuantityCommitted()).isEqualTo(3);
  }

  @Test
  void createFromCart_insufficientStock_throwsValidation() {
    UUID userId = createUserId();
    List<CartItem> items = List.of(cartItem(variant.id(), 100, new BigDecimal("50.00")));

    assertThatThrownBy(() -> orderService.createFromCart(userId, items))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void confirmPayment_transitionsStatusAndDeductsInventory() {
    UUID userId = createUserId();
    List<CartItem> items = List.of(cartItem(variant.id(), 2, new BigDecimal("50.00")));
    Order order = orderService.createFromCart(userId, items);

    orderService.setStripeSession(order.getId(), "cs_test_session123");
    orderService.confirmPayment("cs_test_session123", "pi_test_intent456");

    Order confirmed = orderService.findByStripeSessionId("cs_test_session123");
    assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(confirmed.getStripePaymentIntentId()).isEqualTo("pi_test_intent456");

    InventoryLevel level = levelRepo.findByInventoryItemIdAndLocationId(
        inventoryItemRepo.findByVariantId(variant.id()).orElseThrow().getId(),
        location.getId()).orElseThrow();
    assertThat(level.getQuantityOnHand()).isEqualTo(8);
    assertThat(level.getQuantityCommitted()).isEqualTo(0);
  }

  @Test
  void cancelBySession_releasesReservation() {
    UUID userId = createUserId();
    List<CartItem> items = List.of(cartItem(variant.id(), 2, new BigDecimal("50.00")));
    Order order = orderService.createFromCart(userId, items);
    orderService.setStripeSession(order.getId(), "cs_test_expire_session");

    orderService.cancelBySession("cs_test_expire_session");

    Order cancelled = orderService.findByStripeSessionId("cs_test_expire_session");
    assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);

    InventoryLevel level = levelRepo.findByInventoryItemIdAndLocationId(
        inventoryItemRepo.findByVariantId(variant.id()).orElseThrow().getId(),
        location.getId()).orElseThrow();
    assertThat(level.getQuantityCommitted()).isEqualTo(0);
  }

  @Test
  void cancelOrder_fromPendingPayment_succeeds() {
    UUID userId = createUserId();
    List<CartItem> items = List.of(cartItem(variant.id(), 1, new BigDecimal("50.00")));
    Order order = orderService.createFromCart(userId, items);

    orderService.cancelOrder(order.getId());

    Order cancelled = orderService.getById(order.getId());
    assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void cancelOrder_fromPaid_throwsConflict() {
    UUID userId = createUserId();
    List<CartItem> items = List.of(cartItem(variant.id(), 1, new BigDecimal("50.00")));
    Order order = orderService.createFromCart(userId, items);
    orderService.setStripeSession(order.getId(), "cs_test_paid");
    orderService.confirmPayment("cs_test_paid", "pi_test");

    assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void createFromCart_emptyItems_throwsValidation() {
    UUID userId = createUserId();

    assertThatThrownBy(() -> orderService.createFromCart(userId, List.of()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void createFromCart_inactiveProduct_throwsValidation() {
    UUID userId = createUserId();

    // Create a DRAFT product (do NOT call changeStatus to ACTIVE)
    AdminProductResponse draftProduct = productService.create(
        new CreateProductRequest("Draft Product", null, null, null, null, List.of()));
    AdminVariantResponse draftVariant = variantService.create(draftProduct.id(),
        new CreateVariantRequest(new BigDecimal("25.00"), null, null, null, null, null, List.of()));

    assertThatThrownBy(() -> orderService.createFromCart(userId,
        List.of(cartItem(draftVariant.id(), 1, BigDecimal.ONE))))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void confirmPayment_calledTwice_isIdempotent() {
    UUID userId = createUserId();
    List<CartItem> items = List.of(cartItem(variant.id(), 2, new BigDecimal("50.00")));
    Order order = orderService.createFromCart(userId, items);

    orderService.setStripeSession(order.getId(), "cs_test_idempotent");
    orderService.confirmPayment("cs_test_idempotent", "pi_test_idempotent");
    // Second call — must not throw
    orderService.confirmPayment("cs_test_idempotent", "pi_test_idempotent");

    Order confirmed = orderService.findByStripeSessionId("cs_test_idempotent");
    assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.PAID);

    InventoryLevel level = levelRepo.findByInventoryItemIdAndLocationId(
        inventoryItemRepo.findByVariantId(variant.id()).orElseThrow().getId(),
        location.getId()).orElseThrow();
    assertThat(level.getQuantityOnHand()).isEqualTo(8); // 10 - 2, only deducted once
  }

  @Test
  void cancelOrder_alreadyCancelled_isNoOp() {
    UUID userId = createUserId();
    List<CartItem> items = List.of(cartItem(variant.id(), 1, new BigDecimal("50.00")));
    Order order = orderService.createFromCart(userId, items);

    orderService.cancelOrder(order.getId());
    // Second cancel — must not throw
    orderService.cancelOrder(order.getId());

    Order cancelled = orderService.getById(order.getId());
    assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void cancelBySession_unknownSession_isNoOp() {
    // Should silently do nothing when session doesn't exist
    orderService.cancelBySession("cs_nonexistent_session");
  }
}
