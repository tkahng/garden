package io.k2dv.garden.order.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.order.dto.OrderEventResponse;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderEventType;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventServiceIT extends AbstractIntegrationTest {

    @Autowired
    OrderEventService orderEventService;
    @Autowired
    OrderRepository orderRepo;
    @Autowired
    AuthService authService;
    @Autowired
    UserRepository userRepo;
    @MockitoBean
    EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID orderId;
    private UUID authorId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "event-test-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        UUID userId = userRepo.findByEmail(email).orElseThrow().getId();
        authorId = userId;

        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("100.00"));
        orderId = orderRepo.save(order).getId();
    }

    @Test
    void emit_savesEventAndReturnsResponse() {
        OrderEventResponse resp = orderEventService.emit(
            orderId, OrderEventType.ORDER_PLACED, "Order placed", authorId, "Test User", null);

        assertThat(resp.id()).isNotNull();
        assertThat(resp.orderId()).isEqualTo(orderId);
        assertThat(resp.type()).isEqualTo(OrderEventType.ORDER_PLACED);
        assertThat(resp.message()).isEqualTo("Order placed");
        assertThat(resp.authorId()).isEqualTo(authorId);
        assertThat(resp.authorName()).isEqualTo("Test User");
        assertThat(resp.metadata()).isNull();
    }

    @Test
    void emit_withMetadata_persistsMap() {
        Map<String, Object> meta = Map.of("stripeSession", "cs_test_123", "amount", 9999);

        OrderEventResponse resp = orderEventService.emit(
            orderId, OrderEventType.PAYMENT_CONFIRMED, null, null, null, meta);

        assertThat(resp.metadata()).containsEntry("stripeSession", "cs_test_123");
        assertThat(resp.metadata()).containsKey("amount");
    }

    @Test
    void emit_withNullOptionalFields_savesSuccessfully() {
        OrderEventResponse resp = orderEventService.emit(
            orderId, OrderEventType.NOTE_ADDED, null, null, null, null);

        assertThat(resp.id()).isNotNull();
        assertThat(resp.message()).isNull();
        assertThat(resp.authorId()).isNull();
        assertThat(resp.authorName()).isNull();
        assertThat(resp.metadata()).isNull();
    }

    @Test
    void list_returnsEventsInChronologicalOrder() {
        orderEventService.emit(orderId, OrderEventType.ORDER_PLACED, "first", null, null, null);
        orderEventService.emit(orderId, OrderEventType.PAYMENT_CONFIRMED, "second", null, null, null);
        orderEventService.emit(orderId, OrderEventType.FULFILLMENT_CREATED, "third", null, null, null);

        List<OrderEventResponse> events = orderEventService.list(orderId);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).type()).isEqualTo(OrderEventType.ORDER_PLACED);
        assertThat(events.get(1).type()).isEqualTo(OrderEventType.PAYMENT_CONFIRMED);
        assertThat(events.get(2).type()).isEqualTo(OrderEventType.FULFILLMENT_CREATED);
        // Verify ascending createdAt order
        assertThat(events.get(0).createdAt()).isBeforeOrEqualTo(events.get(1).createdAt());
        assertThat(events.get(1).createdAt()).isBeforeOrEqualTo(events.get(2).createdAt());
    }

    @Test
    void list_unknownOrderId_returnsEmptyList() {
        List<OrderEventResponse> events = orderEventService.list(UUID.randomUUID());
        assertThat(events).isEmpty();
    }

    @Test
    void list_isolatedByOrderId_onlyReturnsOwnEvents() {
        // Create a second order for another user
        int n = counter.incrementAndGet();
        String email2 = "event-test-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email2, "password1", "Other", "User"));
        UUID userId2 = userRepo.findByEmail(email2).orElseThrow().getId();
        Order order2 = new Order();
        order2.setUserId(userId2);
        order2.setTotalAmount(new BigDecimal("50.00"));
        UUID orderId2 = orderRepo.save(order2).getId();

        orderEventService.emit(orderId, OrderEventType.ORDER_PLACED, "for order 1", null, null, null);
        orderEventService.emit(orderId2, OrderEventType.ORDER_PLACED, "for order 2", null, null, null);

        List<OrderEventResponse> events1 = orderEventService.list(orderId);
        List<OrderEventResponse> events2 = orderEventService.list(orderId2);

        assertThat(events1).hasSize(1);
        assertThat(events1.get(0).message()).isEqualTo("for order 1");
        assertThat(events2).hasSize(1);
        assertThat(events2.get(0).message()).isEqualTo("for order 2");
    }
}
