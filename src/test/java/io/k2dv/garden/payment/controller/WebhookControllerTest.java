package io.k2dv.garden.payment.controller;

import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.payment.config.StripeProperties;
import io.k2dv.garden.payment.service.PaymentService;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebhookController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class WebhookControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PaymentService paymentService;
    @MockitoBean StripeProperties stripeProperties;

    @Test
    void webhook_validEvent_returns200() throws Exception {
        when(stripeProperties.getWebhookSecret()).thenReturn("whsec_stub");

        mvc.perform(post("/api/v1/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=123,v1=abc")
                .content("{\"type\":\"checkout.session.completed\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void webhook_invalidSignature_returns400() throws Exception {
        when(stripeProperties.getWebhookSecret()).thenReturn("whsec_stub");
        doThrow(new ValidationException("INVALID_WEBHOOK_SIGNATURE", "Invalid signature"))
            .when(paymentService).handleWebhook(any(), any(), any());

        mvc.perform(post("/api/v1/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=bad,v1=bad")
                .content("{\"type\":\"checkout.session.completed\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_WEBHOOK_SIGNATURE"));
    }
}
