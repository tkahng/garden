package io.k2dv.garden.quote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.quote.dto.AddQuoteCartItemRequest;
import io.k2dv.garden.quote.dto.QuoteCartItemResponse;
import io.k2dv.garden.quote.dto.QuoteCartResponse;
import io.k2dv.garden.quote.dto.UpdateQuoteCartItemRequest;
import io.k2dv.garden.quote.model.QuoteCartStatus;
import io.k2dv.garden.quote.service.QuoteCartService;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = QuoteCartController.class)
@Import({TestSecurityConfig.class, TestCurrentUserConfig.class, GlobalExceptionHandler.class})
class QuoteCartControllerTest {

    @Autowired
    MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean
    QuoteCartService quoteCartService;

    private QuoteCartResponse emptyCart() {
        return new QuoteCartResponse(UUID.randomUUID(), QuoteCartStatus.ACTIVE, List.of(), Instant.now());
    }

    private QuoteCartResponse cartWithItem(UUID itemId, UUID variantId) {
        return new QuoteCartResponse(UUID.randomUUID(), QuoteCartStatus.ACTIVE,
            List.of(new QuoteCartItemResponse(itemId, variantId, 1, null, Instant.now())),
            Instant.now());
    }

    @Test
    void getCart_returns200() throws Exception {
        when(quoteCartService.getOrCreateActiveCart(any())).thenReturn(emptyCart());

        mvc.perform(get("/api/v1/quote-cart"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void clearCart_returns204() throws Exception {
        doNothing().when(quoteCartService).clearCart(any());

        mvc.perform(delete("/api/v1/quote-cart"))
            .andExpect(status().isNoContent());
    }

    @Test
    void addItem_returns200() throws Exception {
        UUID variantId = UUID.randomUUID();
        when(quoteCartService.addItem(any(), any())).thenReturn(cartWithItem(UUID.randomUUID(), variantId));

        mvc.perform(post("/api/v1/quote-cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new AddQuoteCartItemRequest(variantId, 1, "urgent"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void addItem_variantNotFound_returns404() throws Exception {
        when(quoteCartService.addItem(any(), any()))
            .thenThrow(new NotFoundException("VARIANT_NOT_FOUND", "Not found"));

        mvc.perform(post("/api/v1/quote-cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new AddQuoteCartItemRequest(UUID.randomUUID(), 1, null))))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_returns200() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(quoteCartService.updateItem(any(), any(), any()))
            .thenReturn(cartWithItem(itemId, UUID.randomUUID()));

        mvc.perform(put("/api/v1/quote-cart/items/{itemId}", itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateQuoteCartItemRequest(3, "note"))))
            .andExpect(status().isOk());
    }

    @Test
    void removeItem_returns200() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(quoteCartService.removeItem(any(), any())).thenReturn(emptyCart());

        mvc.perform(delete("/api/v1/quote-cart/items/{itemId}", itemId))
            .andExpect(status().isOk());
    }
}
