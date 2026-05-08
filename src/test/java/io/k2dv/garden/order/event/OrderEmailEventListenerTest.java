package io.k2dv.garden.order.event;

import io.k2dv.garden.auth.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEmailEventListenerTest {

    @Mock
    EmailService emailService;

    @InjectMocks
    OrderEmailEventListener listener;

    @Test
    void onOrderConfirmed_delegatesToEmailService() {
        var event = new OrderConfirmedEvent(
            "buyer@example.com", "#ABC123", new BigDecimal("49.99"),
            "usd", List.of("2 × Tomato Seeds — $7.98"), "http://localhost:3000");

        listener.onOrderConfirmed(event);

        verify(emailService).sendOrderConfirmation(
            "buyer@example.com", "#ABC123", new BigDecimal("49.99"),
            "usd", List.of("2 × Tomato Seeds — $7.98"), "http://localhost:3000");
    }

    @Test
    void onOrderCancelled_delegatesToEmailService() {
        var event = new OrderCancelledEvent("buyer@example.com", "#ABC123", "http://localhost:3000");

        listener.onOrderCancelled(event);

        verify(emailService).sendOrderCancelled("buyer@example.com", "#ABC123", "http://localhost:3000");
    }

    @Test
    void onOrderConfirmed_emailServiceThrows_doesNotPropagate() {
        var event = new OrderConfirmedEvent(
            "bad@example.com", "#XYZ", BigDecimal.ONE, "usd", List.of(), "http://localhost:3000");
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down"))
            .when(emailService).sendOrderConfirmation(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        org.assertj.core.api.Assertions.assertThatCode(() -> listener.onOrderConfirmed(event))
            .doesNotThrowAnyException();
    }
}
