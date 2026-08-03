package io.k2dv.garden.config;

import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.model.CartStatus;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.giftcard.dto.GiftCardValidationResponse;
import io.k2dv.garden.giftcard.service.GiftCardService;
import io.k2dv.garden.order.dto.GuestOrderResponse;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.payment.service.PaymentService;
import io.k2dv.garden.search.dto.SearchResponse;
import io.k2dv.garden.search.service.SearchService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shipping.service.ShippingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class SecurityConfigIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @MockitoBean CartService cartService;
    @MockitoBean GiftCardService giftCardService;
    @MockitoBean ShippingService shippingService;
    @MockitoBean SearchService searchService;
    @MockitoBean OrderService orderService;
    @MockitoBean PaymentService paymentService;

    private static final UUID SESSION_ID = UUID.randomUUID();

    @Test
    void guestCart_get_returns200_withoutAuth() throws Exception {
        when(cartService.getOrCreateGuestCart(eq(SESSION_ID)))
            .thenReturn(new CartResponse(
                UUID.randomUUID(), CartStatus.ACTIVE, SESSION_ID, "USD", null,
                List.of(), null));

        mvc.perform(get("/api/v1/guest-cart")
                .header("X-Guest-Session", SESSION_ID.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void guestCart_post_returns200_withoutAuth() throws Exception {
        when(cartService.addGuestItem(eq(SESSION_ID), any()))
            .thenReturn(new CartResponse(
                UUID.randomUUID(), CartStatus.ACTIVE, SESSION_ID, "USD", null,
                List.of(), null));

        mvc.perform(post("/api/v1/guest-cart/items")
                .header("X-Guest-Session", SESSION_ID.toString())
                .contentType("application/json")
                .content("{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}"))
            .andExpect(status().isOk());
    }

    @Test
    void guestCart_put_returns200_withoutAuth() throws Exception {
        when(cartService.updateGuestItem(eq(SESSION_ID), any(), any()))
            .thenReturn(new CartResponse(
                UUID.randomUUID(), CartStatus.ACTIVE, SESSION_ID, "USD", null,
                List.of(), null));

        mvc.perform(put("/api/v1/guest-cart/items/{id}", UUID.randomUUID())
                .header("X-Guest-Session", SESSION_ID.toString())
                .contentType("application/json")
                .content("{\"quantity\":3}"))
            .andExpect(status().isOk());
    }

    @Test
    void guestCart_delete_returns204_withoutAuth() throws Exception {
        mvc.perform(delete("/api/v1/guest-cart")
                .header("X-Guest-Session", SESSION_ID.toString()))
            .andExpect(status().isNoContent());
    }

    @Test
    void guestCart_deleteItem_returns200_withoutAuth() throws Exception {
        when(cartService.removeGuestItem(eq(SESSION_ID), any()))
            .thenReturn(new CartResponse(
                UUID.randomUUID(), CartStatus.ACTIVE, SESSION_ID, "USD", null,
                List.of(), null));

        mvc.perform(delete("/api/v1/guest-cart/items/{id}", UUID.randomUUID())
                .header("X-Guest-Session", SESSION_ID.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void shippingRates_returns200_withoutAuth() throws Exception {
        when(shippingService.findRatesForAddress(eq("US"), any(), any()))
            .thenReturn(List.of());

        mvc.perform(get("/api/v1/storefront/shipping/rates")
                .param("country", "US"))
            .andExpect(status().isOk());
    }

    @Test
    void giftCardValidate_returns200_withoutAuth() throws Exception {
        when(giftCardService.validate(eq("GIFT-1234")))
            .thenReturn(new GiftCardValidationResponse(true, "GIFT-1234",
                new BigDecimal("50.00"), "USD", null));

        mvc.perform(get("/api/v1/storefront/gift-cards/validate")
                .param("code", "GIFT-1234"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_returns200_withoutAuth() throws Exception {
        var emptyPage = new PagedResult(
            List.of(),
            PageMeta.builder().page(0).pageSize(10).total(0).build());

        when(searchService.search(eq("widget"), any(), any()))
            .thenReturn(new SearchResponse(
                emptyPage, emptyPage, emptyPage, emptyPage));

        mvc.perform(get("/api/v1/search")
                .param("q", "widget"))
            .andExpect(status().isOk());
    }

    @Test
    void guestOrderLookup_returns200_withoutAuth() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.lookupGuestOrder(eq(orderId), eq("guest@example.com")))
            .thenReturn(new GuestOrderResponse(orderId, "guest@example.com",
                OrderStatus.PAID, new BigDecimal("100.00"), "usd", null, null,
                null, null, null, null, List.of(), null));

        mvc.perform(get("/api/v1/checkout/orders/{orderId}/lookup", orderId)
                .param("guestEmail", "guest@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(orderId.toString()))
            .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    void authRequiredEndpoint_returns401_withoutAuth() throws Exception {
        mvc.perform(get("/api/v1/cart"))
            .andExpect(status().isUnauthorized());
    }
}
