package io.k2dv.garden.fulfillment.event;

import io.k2dv.garden.auth.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FulfillmentEmailEventListenerTest {

    @Mock
    EmailService emailService;

    @InjectMocks
    FulfillmentEmailEventListener listener;

    @Test
    void onFulfillmentShipped_delegatesToEmailService() {
        var event = new FulfillmentShippedEvent(
            "buyer@example.com", "#ABC123", "1Z9999", "UPS",
            "https://ups.com/track?n=1Z9999", "http://localhost:3000");

        listener.onFulfillmentShipped(event);

        verify(emailService).sendShippingNotification(
            "buyer@example.com", "#ABC123", "1Z9999", "UPS",
            "https://ups.com/track?n=1Z9999", "http://localhost:3000");
    }

    @Test
    void onFulfillmentDelivered_delegatesToEmailService() {
        var event = new FulfillmentDeliveredEvent(
            "buyer@example.com", "#ABC123", "http://localhost:3000");

        listener.onFulfillmentDelivered(event);

        verify(emailService).sendOrderDelivered(
            "buyer@example.com", "#ABC123", null, "http://localhost:3000");
    }

    @Test
    void onFulfillmentShipped_emailServiceThrows_doesNotPropagate() {
        var event = new FulfillmentShippedEvent(
            "bad@example.com", "#XYZ", null, null, null, "http://localhost:3000");
        doThrow(new RuntimeException("SMTP down"))
            .when(emailService).sendShippingNotification(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertThatCode(() -> listener.onFulfillmentShipped(event))
            .doesNotThrowAnyException();
    }

    @Test
    void onFulfillmentDelivered_emailServiceThrows_doesNotPropagate() {
        var event = new FulfillmentDeliveredEvent(
            "bad@example.com", "#XYZ", "http://localhost:3000");
        doThrow(new RuntimeException("SMTP down"))
            .when(emailService).sendOrderDelivered(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertThatCode(() -> listener.onFulfillmentDelivered(event))
            .doesNotThrowAnyException();
    }
}
