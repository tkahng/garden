package io.k2dv.garden.payment.controller;

import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.payment.dto.CheckoutResponse;
import io.k2dv.garden.payment.dto.CheckoutReturnResponse;
import io.k2dv.garden.payment.exception.PaymentException;
import io.k2dv.garden.payment.service.PaymentService;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CheckoutController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class, TestCurrentUserConfig.class})
class CheckoutControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PaymentService paymentService;

    @Test
    void checkout_happyPath_returns200WithUrl() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(paymentService.initiateCheckout(any()))
            .thenReturn(new CheckoutResponse("https://checkout.stripe.com/pay/cs_test_123", orderId));

        mvc.perform(post("/api/v1/checkout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.checkoutUrl").value("https://checkout.stripe.com/pay/cs_test_123"))
            .andExpect(jsonPath("$.data.orderId").value(orderId.toString()));
    }

    @Test
    void checkout_emptyCart_returns400() throws Exception {
        when(paymentService.initiateCheckout(any()))
            .thenThrow(new ValidationException("EMPTY_CART", "Cart is empty"));

        mvc.perform(post("/api/v1/checkout"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("EMPTY_CART"));
    }

    @Test
    void checkout_stripeFailure_returns502() throws Exception {
        when(paymentService.initiateCheckout(any()))
            .thenThrow(new PaymentException("STRIPE_ERROR", "Stripe failed"));

        mvc.perform(post("/api/v1/checkout"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").value("STRIPE_ERROR"));
    }

    @Test
    void checkoutReturn_returns200WithStatus() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(paymentService.verifyReturn(any(), any()))
            .thenReturn(new CheckoutReturnResponse(orderId, OrderStatus.PAID));

        mvc.perform(get("/api/v1/checkout/return").param("session_id", "cs_test_abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PAID"))
            .andExpect(jsonPath("$.data.orderId").value(orderId.toString()));
    }

    @Test
    void checkoutReturn_sessionNotFound_returns404() throws Exception {
        when(paymentService.verifyReturn(any(), any()))
            .thenThrow(new NotFoundException("ORDER_NOT_FOUND", "Not found"));

        mvc.perform(get("/api/v1/checkout/return").param("session_id", "cs_missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
    }

    @Test
    void checkoutReturn_wrongUser_returns400() throws Exception {
        when(paymentService.verifyReturn(any(), any()))
            .thenThrow(new ValidationException("ORDER_NOT_OWNED", "Not yours"));

        mvc.perform(get("/api/v1/checkout/return").param("session_id", "cs_stolen"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("ORDER_NOT_OWNED"));
    }
}
