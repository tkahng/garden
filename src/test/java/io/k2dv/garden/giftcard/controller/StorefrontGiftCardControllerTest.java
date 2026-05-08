package io.k2dv.garden.giftcard.controller;

import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.giftcard.dto.GiftCardTransactionResponse;
import io.k2dv.garden.giftcard.dto.GiftCardValidationResponse;
import io.k2dv.garden.giftcard.service.GiftCardService;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorefrontGiftCardController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class, TestCurrentUserConfig.class})
class StorefrontGiftCardControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean GiftCardService giftCardService;

    @Test
    void balance_validCode_returns200WithBalance() throws Exception {
        when(giftCardService.validate(eq("GIFT-1234")))
            .thenReturn(new GiftCardValidationResponse(true, "GIFT-1234",
                new BigDecimal("75.00"), "USD", null));

        mvc.perform(get("/api/v1/storefront/gift-cards/balance").param("code", "GIFT-1234"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true))
            .andExpect(jsonPath("$.data.currentBalance").value(75.0))
            .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void balance_invalidCode_returnsInvalidResponse() throws Exception {
        when(giftCardService.validate(eq("INVALID")))
            .thenReturn(new GiftCardValidationResponse(false, "INVALID",
                null, null, "Gift card not found"));

        mvc.perform(get("/api/v1/storefront/gift-cards/balance").param("code", "INVALID"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(false));
    }

    @Test
    void transactions_validCode_returns200WithList() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        when(giftCardService.listTransactionsByCode(eq("GIFT-1234")))
            .thenReturn(List.of(new GiftCardTransactionResponse(
                txId, cardId, new BigDecimal("-25.00"), UUID.randomUUID(),
                "Redeemed on order", Instant.now())));

        mvc.perform(get("/api/v1/storefront/gift-cards/transactions").param("code", "GIFT-1234"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].delta").value(-25.0));
    }

    @Test
    void transactions_notFound_returns404() throws Exception {
        when(giftCardService.listTransactionsByCode(eq("NOPE")))
            .thenThrow(new NotFoundException("GIFT_CARD_NOT_FOUND", "Gift card not found"));

        mvc.perform(get("/api/v1/storefront/gift-cards/transactions").param("code", "NOPE"))
            .andExpect(status().isNotFound());
    }
}
