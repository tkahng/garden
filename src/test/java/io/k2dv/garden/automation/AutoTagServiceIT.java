package io.k2dv.garden.automation;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AutoTagServiceIT extends AbstractIntegrationTest {

    @Autowired AutoTagService autoTagService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired OrderRepository orderRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "tag-user-" + n + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Tag", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();
    }

    private void paidOrder(BigDecimal amount) {
        Order o = new Order();
        o.setUserId(userId);
        o.setTotalAmount(amount);
        o.setStatus(OrderStatus.PAID);
        orderRepo.save(o);
    }

    @Test
    void applyOrderTags_noOrders_setsNoTags() {
        autoTagService.applyOrderTags(userId);

        User user = userRepo.findById(userId).orElseThrow();
        assertThat(user.getTags()).isEmpty();
    }

    @Test
    void applyOrderTags_firstOrder_addsFirstTimeBuyer() {
        paidOrder(new BigDecimal("50.00"));

        autoTagService.applyOrderTags(userId);

        User user = userRepo.findById(userId).orElseThrow();
        assertThat(user.getTags()).contains("first-time-buyer");
        assertThat(user.getTags()).doesNotContain("repeat-customer");
    }

    @Test
    void applyOrderTags_secondOrder_swapsToRepeatCustomer() {
        paidOrder(new BigDecimal("50.00"));
        paidOrder(new BigDecimal("50.00"));

        autoTagService.applyOrderTags(userId);

        User user = userRepo.findById(userId).orElseThrow();
        assertThat(user.getTags()).contains("repeat-customer");
        assertThat(user.getTags()).doesNotContain("first-time-buyer");
    }

    @Test
    void applyOrderTags_fiveOrMoreOrders_addsLoyalCustomer() {
        for (int i = 0; i < 5; i++) {
            paidOrder(new BigDecimal("50.00"));
        }

        autoTagService.applyOrderTags(userId);

        User user = userRepo.findById(userId).orElseThrow();
        assertThat(user.getTags()).contains("loyal-customer");
        assertThat(user.getTags()).contains("repeat-customer");
    }

    @Test
    void applyOrderTags_highSpend_addsVipTag() {
        paidOrder(new BigDecimal("300.00"));
        paidOrder(new BigDecimal("300.00")); // 600 total >= 500 VIP threshold

        autoTagService.applyOrderTags(userId);

        User user = userRepo.findById(userId).orElseThrow();
        assertThat(user.getTags()).contains("vip");
    }

    @Test
    void applyOrderTags_unknownUser_doesNothing() {
        autoTagService.applyOrderTags(UUID.randomUUID()); // must not throw
    }

    @Test
    void applyOrderTags_calledTwice_doesNotDuplicateTags() {
        paidOrder(new BigDecimal("50.00"));

        autoTagService.applyOrderTags(userId);
        autoTagService.applyOrderTags(userId);

        User user = userRepo.findById(userId).orElseThrow();
        long count = user.getTags().stream().filter("first-time-buyer"::equals).count();
        assertThat(count).isEqualTo(1);
    }
}
