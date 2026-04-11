package io.k2dv.garden.quote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.quote.dto.*;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.quote.service.QuoteService;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = QuoteController.class)
@Import({TestSecurityConfig.class, TestCurrentUserConfig.class, GlobalExceptionHandler.class})
class QuoteControllerTest {

    @Autowired
    MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean
    QuoteService quoteService;

    private QuoteRequestResponse stubQuote(UUID id) {
        return new QuoteRequestResponse(id, UUID.randomUUID(), UUID.randomUUID(), null,
            QuoteStatus.PENDING, "123 Main St", null, "City", null, "12345", "US",
            null, null, null, null, null, null, List.of(), Instant.now(), Instant.now());
    }

    @Test
    void submitQuote_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(quoteService.submit(any(), any())).thenReturn(stubQuote(id));

        mvc.perform(post("/api/v1/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SubmitQuoteRequest(
                    UUID.randomUUID(), "123 Main", null, "City", null, "12345", "US", null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void listQuotes_returns200() throws Exception {
        PagedResult<QuoteRequestResponse> result = new PagedResult<>(
            List.of(stubQuote(UUID.randomUUID())),
            PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(quoteService.listForUser(any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/quotes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getQuote_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(quoteService.getForUser(any(), any())).thenReturn(stubQuote(id));

        mvc.perform(get("/api/v1/quotes/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void getQuote_notFound_returns404() throws Exception {
        when(quoteService.getForUser(any(), any()))
            .thenThrow(new NotFoundException("QUOTE_NOT_FOUND", "Not found"));

        mvc.perform(get("/api/v1/quotes/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void acceptQuote_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(quoteService.accept(any(), any()))
            .thenReturn(new QuoteAcceptResponse("https://checkout.stripe.com/pay/cs_test", UUID.randomUUID()));

        mvc.perform(post("/api/v1/quotes/{id}/accept", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.checkoutUrl").exists());
    }

    @Test
    void acceptQuote_expired_returns409() throws Exception {
        when(quoteService.accept(any(), any()))
            .thenThrow(new ConflictException("QUOTE_EXPIRED", "Quote has expired"));

        mvc.perform(post("/api/v1/quotes/{id}/accept", UUID.randomUUID()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("QUOTE_EXPIRED"));
    }

    @Test
    void rejectQuote_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        QuoteRequestResponse rejected = new QuoteRequestResponse(id, UUID.randomUUID(), UUID.randomUUID(),
            null, QuoteStatus.REJECTED, "123 Main", null, "City", null, "12345", "US",
            null, null, null, null, null, null, List.of(), Instant.now(), Instant.now());
        when(quoteService.reject(any(), any())).thenReturn(rejected);

        mvc.perform(post("/api/v1/quotes/{id}/reject", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }
}
