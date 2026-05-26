package io.k2dv.garden.order.event;

import io.k2dv.garden.auth.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEmailEventListener {

    private final EmailService emailService;

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        try {
            emailService.sendOrderConfirmation(
                event.to(), event.orderRef(), event.total(),
                event.currency(), event.itemLines(), event.frontendUrl());
        } catch (Exception e) {
            // Log at ERROR with full stack so alerting / on-call picks this up
            log.error("Failed to send order-confirmation email to {} for order {}: {}",
                event.to(), event.orderRef(), e.getMessage(), e);
        }
    }

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        try {
            emailService.sendOrderCancelled(event.to(), event.orderRef(), event.frontendUrl());
        } catch (Exception e) {
            // Log at ERROR with full stack so alerting / on-call picks this up
            log.error("Failed to send order-cancelled email to {} for order {}: {}",
                event.to(), event.orderRef(), e.getMessage(), e);
        }
    }
}
