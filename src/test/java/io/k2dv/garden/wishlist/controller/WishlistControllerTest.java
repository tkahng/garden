package io.k2dv.garden.wishlist.controller;

import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.wishlist.dto.WishlistItemResponse;
import io.k2dv.garden.wishlist.dto.WishlistResponse;
import io.k2dv.garden.wishlist.service.WishlistService;
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

@WebMvcTest(controllers = WishlistController.class)
@Import({ TestSecurityConfig.class, TestCurrentUserConfig.class, GlobalExceptionHandler.class })
class WishlistControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean WishlistService wishlistService;

    private WishlistResponse emptyWishlist() {
        return new WishlistResponse(UUID.randomUUID(), List.of());
    }

    private WishlistResponse wishlistWithItem(UUID productId) {
        WishlistItemResponse item = new WishlistItemResponse(UUID.randomUUID(), productId,
                "Rose Bush", "rose-bush", null, new BigDecimal("19.99"));
        return new WishlistResponse(UUID.randomUUID(), List.of(item));
    }

    @Test
    void getWishlist_returns200() throws Exception {
        when(wishlistService.getWishlist(any())).thenReturn(emptyWishlist());

        mvc.perform(get("/api/v1/account/wishlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void addItem_returns201() throws Exception {
        UUID productId = UUID.randomUUID();
        when(wishlistService.addItem(any(), any())).thenReturn(wishlistWithItem(productId));

        mvc.perform(post("/api/v1/account/wishlist/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void addItem_missingProductId_returns400() throws Exception {
        mvc.perform(post("/api/v1/account/wishlist/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_productNotFound_returns404() throws Exception {
        when(wishlistService.addItem(any(), any()))
                .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Not found"));

        mvc.perform(post("/api/v1/account/wishlist/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_duplicate_returns409() throws Exception {
        when(wishlistService.addItem(any(), any()))
                .thenThrow(new ConflictException("ALREADY_IN_WISHLIST", "Already added"));

        mvc.perform(post("/api/v1/account/wishlist/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_IN_WISHLIST"));
    }

    @Test
    void removeItem_returns200() throws Exception {
        when(wishlistService.removeItem(any(), any())).thenReturn(emptyWishlist());

        mvc.perform(delete("/api/v1/account/wishlist/items/{productId}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void removeItem_noWishlist_returns404() throws Exception {
        when(wishlistService.removeItem(any(), any()))
                .thenThrow(new NotFoundException("WISHLIST_NOT_FOUND", "Not found"));

        mvc.perform(delete("/api/v1/account/wishlist/items/{productId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WISHLIST_NOT_FOUND"));
    }
}
