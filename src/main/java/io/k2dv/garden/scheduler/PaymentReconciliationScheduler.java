package io.k2dv.garden.scheduler;

import io.k2dv.garden.order.dto.OrderResponse;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationScheduler {

    static final int SPIKE_THRESHOLD = 10;

    private final OrderRepository orderRepo;
    private final OrderService orderService;

    @Scheduled(cron = "0 */10 * * * *")
    @SchedulerLock(name = "reconcileStalePendingPayments", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    public void reconcileStalePendingPayments() {
        doReconcile();
    }

    public void doReconcile() {
        Instant staleBefore = Instant.now().minus(15, ChronoUnit.MINUTES);
        List<Order> stale = orderRepo.findByStatusAndStripeSessionIdIsNotNullAndCreatedAtBefore(
            OrderStatus.PENDING_PAYMENT, staleBefore);
        if (stale.isEmpty()) return;

        if (stale.size() >= SPIKE_THRESHOLD) {
            log.warn("PAYMENT_RECONCILIATION_SPIKE: {} stale PENDING_PAYMENT orders detected — " +
                "possible Stripe webhook outage", stale.size());
        } else {
            log.info("Reconciling {} stale PENDING_PAYMENT order(s)", stale.size());
        }

        int confirmed = 0, cancelled = 0, skipped = 0, errors = 0;
        for (Order order : stale) {
            try {
                OrderResponse result = orderService.syncPaymentFromStripe(order.getId());
                if (result.status() == OrderStatus.PAID) confirmed++;
                else if (result.status() == OrderStatus.CANCELLED) cancelled++;
                else skipped++;
            } catch (Exception e) {
                errors++;
                log.warn("Failed to reconcile order {}: {}", order.getId(), e.getMessage());
            }
        }
        log.info("Reconciliation complete: {} confirmed, {} cancelled, {} skipped, {} errors",
            confirmed, cancelled, skipped, errors);
    }
}
