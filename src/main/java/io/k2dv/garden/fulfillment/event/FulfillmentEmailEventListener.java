package io.k2dv.garden.fulfillment.event;

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
public class FulfillmentEmailEventListener {

    private final EmailService emailService;

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFulfillmentShipped(FulfillmentShippedEvent event) {
        try {
            emailService.sendShippingNotification(
                event.to(), event.orderRef(), event.trackingNumber(),
                event.trackingCompany(), event.trackingUrl(), event.frontendUrl());
        } catch (Exception e) {
            log.error("Failed to send shipping notification to {} for order {}: {}",
                event.to(), event.orderRef(), e.getMessage(), e);
        }
    }

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFulfillmentDelivered(FulfillmentDeliveredEvent event) {
        try {
            emailService.sendOrderDelivered(event.to(), event.orderRef(), null, event.frontendUrl());
        } catch (Exception e) {
            log.error("Failed to send delivery notification to {} for order {}: {}",
                event.to(), event.orderRef(), e.getMessage(), e);
        }
    }
}
