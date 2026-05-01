package io.k2dv.garden.cart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.dto.CartItemProductInfo;
import io.k2dv.garden.cart.dto.CartItemResponse;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.dto.UpdateCartItemRequest;
import io.k2dv.garden.cart.model.CartStatus;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CartController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class, TestCurrentUserConfig.class})
class CartControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean CartService cartService;

    private CartResponse stubCart(UUID id) {
        UUID productId = UUID.randomUUID();
        CartItemProductInfo productInfo = new CartItemProductInfo(
            productId, "Test Product", "Default Title", null);
        return new CartResponse(id, CartStatus.ACTIVE, null,
            List.of(new CartItemResponse(UUID.randomUUID(), UUID.randomUUID(), 2, new BigDecimal("49.99"), productInfo)),
            /* createdAt */ null);
    }

    @Test
    void getCart_returns200() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.getOrCreateActiveCart(any())).thenReturn(stubCart(cartId));

        mvc.perform(get("/api/v1/cart"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(cartId.toString()))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.items[0].product.productTitle").value("Test Product"))
            .andExpect(jsonPath("$.data.items[0].product.variantTitle").value("Default Title"));
    }

    @Test
    void addItem_validRequest_returns200() throws Exception {
        when(cartService.addItem(any(), any())).thenReturn(stubCart(UUID.randomUUID()));

        mvc.perform(post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new AddCartItemRequest(UUID.randomUUID(), 2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void addItem_missingVariantId_returns400() throws Exception {
        mvc.perform(post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateItem_returns200() throws Exception {
        when(cartService.updateItem(any(), any(), any())).thenReturn(stubCart(UUID.randomUUID()));

        mvc.perform(put("/api/v1/cart/items/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new UpdateCartItemRequest(3))))
            .andExpect(status().isOk());
    }

    @Test
    void updateItem_notFound_returns404() throws Exception {
        when(cartService.updateItem(any(), any(), any()))
            .thenThrow(new NotFoundException("CART_ITEM_NOT_FOUND", "Not found"));

        mvc.perform(put("/api/v1/cart/items/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new UpdateCartItemRequest(1))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    void removeItem_returns200() throws Exception {
        when(cartService.removeItem(any(), any())).thenReturn(stubCart(UUID.randomUUID()));

        mvc.perform(delete("/api/v1/cart/items/{id}", UUID.randomUUID()))
            .andExpect(status().isOk());
    }

    @Test
    void deleteCart_returns204() throws Exception {
        mvc.perform(delete("/api/v1/cart"))
            .andExpect(status().isNoContent());
    }
}
