package io.k2dv.garden.stats.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.stats.dto.StatsResponse;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatsServiceIT extends AbstractIntegrationTest {

    @Autowired StatsService statsService;
    @Autowired OrderRepository orderRepo;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "stats-it-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Stats", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();
    }

    // ---- date range validation ----

    @Test
    void getStats_fromAfterTo_throwsValidation() {
        Instant from = Instant.now();
        Instant to = from.minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> statsService.getStats(from, to))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode").isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void getStats_fromEqualsTo_returnsZeros() {
        Instant now = Instant.now();
        StatsResponse resp = statsService.getStats(now, now);
        assertThat(resp.orderCount()).isZero();
        assertThat(resp.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.averageOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- empty range ----

    @Test
    void getStats_noOrdersInRange_returnsZeros() {
        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now().minus(6, ChronoUnit.DAYS);

        StatsResponse resp = statsService.getStats(from, to);
        assertThat(resp.orderCount()).isZero();
        assertThat(resp.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.averageOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- with paid orders ----

    @Test
    void getStats_withPaidOrders_returnsCorrectAggregates() {
        // Create two PAID orders directly
        Order o1 = new Order();
        o1.setUserId(userId);
        o1.setTotalAmount(new BigDecimal("100.00"));
        o1.setStatus(OrderStatus.PAID);
        orderRepo.save(o1);

        Order o2 = new Order();
        o2.setUserId(userId);
        o2.setTotalAmount(new BigDecimal("50.00"));
        o2.setStatus(OrderStatus.PAID);
        orderRepo.save(o2);

        // A PENDING order that should NOT be counted
        Order o3 = new Order();
        o3.setUserId(userId);
        o3.setTotalAmount(new BigDecimal("200.00"));
        o3.setStatus(OrderStatus.PENDING_PAYMENT);
        orderRepo.save(o3);

        Instant from = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant to = Instant.now().plus(1, ChronoUnit.MINUTES);

        StatsResponse resp = statsService.getStats(from, to);
        assertThat(resp.orderCount()).isEqualTo(2L);
        assertThat(resp.totalRevenue()).isEqualByComparingTo("150.00");
        assertThat(resp.averageOrderValue()).isEqualByComparingTo("75.00");
    }

    @Test
    void getStats_newCustomerCount_reflectsUsersCreatedInRange() {
        Instant from = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant to = Instant.now().plus(1, ChronoUnit.MINUTES);

        StatsResponse resp = statsService.getStats(from, to);
        // The user created in setUp should be counted
        assertThat(resp.newCustomerCount()).isGreaterThanOrEqualTo(1L);
    }
}
