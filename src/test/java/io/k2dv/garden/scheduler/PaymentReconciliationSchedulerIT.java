package io.k2dv.garden.scheduler;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
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
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.payment.gateway.StripeGateway;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentReconciliationSchedulerIT extends AbstractIntegrationTest {

    @Autowired PaymentReconciliationScheduler scheduler;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepo;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired ProductService productService;
    @Autowired VariantService variantService;
    @Autowired LocationRepository locationRepo;
    @Autowired InventoryItemRepository inventoryItemRepo;
    @Autowired InventoryLevelRepository levelRepo;
    @MockitoBean StripeGateway stripeGateway;
    @MockitoBean EmailService emailService;
    @PersistenceContext EntityManager em;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private AdminVariantResponse variant;
    private Location location;

    @BeforeEach
    void setUp() {
        doNothing().when(emailService).sendEmailVerification(any(), any());
        int n = counter.incrementAndGet();
        AdminProductResponse product = productService.create(
            new CreateProductRequest("Reconcile Product " + n, null, null, null, null, List.of(), null, null));
        productService.changeStatus(product.id(),
            new io.k2dv.garden.product.dto.ProductStatusRequest(ProductStatus.ACTIVE));
        variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("50.00"), null, null, null, null, null, List.of()));

        location = new Location();
        location.setName("Reconcile Warehouse");
        location = locationRepo.save(location);

        InventoryLevel level = new InventoryLevel();
        level.setInventoryItem(inventoryItemRepo.findByVariantId(variant.id()).orElseThrow());
        level.setLocation(location);
        level.setQuantityOnHand(100);
        levelRepo.save(level);
    }

    private UUID createUserId() {
        int n = counter.incrementAndGet();
        String email = "reconcile-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        return userRepo.findByEmail(email).orElseThrow().getId();
    }

    private CartItem cartItem() {
        CartItem item = new CartItem();
        item.setVariantId(variant.id());
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("50.00"));
        return item;
    }

    private Order staleOrder(String sessionId) {
        UUID userId = createUserId();
        Order order = orderService.createFromCart(userId, List.of(cartItem()));
        orderService.setStripeSession(order.getId(), sessionId);
        em.flush();
        em.createNativeQuery("UPDATE checkout.orders SET created_at = :time WHERE id = :id")
            .setParameter("time", Timestamp.from(Instant.now().minus(20, ChronoUnit.MINUTES)))
            .setParameter("id", order.getId())
            .executeUpdate();
        em.clear();
        return orderRepo.findById(order.getId()).orElseThrow();
    }

    private Order recentOrder(String sessionId) {
        UUID userId = createUserId();
        Order order = orderService.createFromCart(userId, List.of(cartItem()));
        orderService.setStripeSession(order.getId(), sessionId);
        return order;
    }

    private Session completedSession(String paymentIntent) {
        Session session = mock(Session.class);
        when(session.getStatus()).thenReturn("complete");
        when(session.getPaymentIntent()).thenReturn(paymentIntent);
        when(session.getTotalDetails()).thenReturn(null);
        return session;
    }

    private Session expiredSession() {
        Session session = mock(Session.class);
        when(session.getStatus()).thenReturn("expired");
        return session;
    }

    private Session openSession() {
        Session session = mock(Session.class);
        when(session.getStatus()).thenReturn("open");
        return session;
    }

    @Test
    void doReconcile_confirmsCompletedSession() throws Exception {
        Order order = staleOrder("cs_reconcile_complete");
        Session session = completedSession("pi_reconciled");
        when(stripeGateway.retrieveSession("cs_reconcile_complete")).thenReturn(session);

        scheduler.doReconcile();

        assertThat(orderRepo.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void doReconcile_cancelsExpiredSession() throws Exception {
        Order order = staleOrder("cs_reconcile_expired");
        Session session = expiredSession();
        when(stripeGateway.retrieveSession("cs_reconcile_expired")).thenReturn(session);

        scheduler.doReconcile();

        assertThat(orderRepo.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void doReconcile_skipsOpenSession() throws Exception {
        Order order = staleOrder("cs_reconcile_open");
        Session session = openSession();
        when(stripeGateway.retrieveSession("cs_reconcile_open")).thenReturn(session);

        scheduler.doReconcile();

        assertThat(orderRepo.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void doReconcile_ignoresRecentOrders() throws Exception {
        Order recent = recentOrder("cs_reconcile_recent");
        Session session = completedSession("pi_recent");
        when(stripeGateway.retrieveSession(any())).thenReturn(session);

        scheduler.doReconcile();

        assertThat(orderRepo.findById(recent.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void doReconcile_ignoresOrdersWithoutStripeSession() throws Exception {
        UUID userId = createUserId();
        Order noSession = orderService.createFromCart(userId, List.of(cartItem()));
        em.flush();
        em.createNativeQuery("UPDATE checkout.orders SET created_at = :time WHERE id = :id")
            .setParameter("time", Timestamp.from(Instant.now().minus(20, ChronoUnit.MINUTES)))
            .setParameter("id", noSession.getId())
            .executeUpdate();
        em.clear();

        scheduler.doReconcile();

        assertThat(orderRepo.findById(noSession.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void doReconcile_continuesAfterStripeErrorOnOneOrder() throws Exception {
        Order errorOrder = staleOrder("cs_reconcile_error");
        Order goodOrder = staleOrder("cs_reconcile_good");
        Session goodSession = completedSession("pi_good");

        when(stripeGateway.retrieveSession("cs_reconcile_error")).thenThrow(mock(StripeException.class));
        when(stripeGateway.retrieveSession("cs_reconcile_good")).thenReturn(goodSession);

        scheduler.doReconcile();

        assertThat(orderRepo.findById(errorOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderRepo.findById(goodOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void doReconcile_processesAllOrdersAtSpikeThreshold() throws Exception {
        int count = PaymentReconciliationScheduler.SPIKE_THRESHOLD;
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Order o = staleOrder("cs_spike_" + i);
            orders.add(o);
            Session session = completedSession("pi_spike_" + i);
            when(stripeGateway.retrieveSession("cs_spike_" + i)).thenReturn(session);
        }

        scheduler.doReconcile();

        for (Order o : orders) {
            assertThat(orderRepo.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }

}
