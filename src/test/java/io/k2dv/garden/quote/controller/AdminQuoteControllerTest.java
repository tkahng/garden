package io.k2dv.garden.quote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.quote.dto.*;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.quote.service.QuoteService;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminQuoteController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminQuoteControllerTest {

    @Autowired
    MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean
    QuoteService quoteService;

    private QuoteRequestResponse stubQuote(UUID id, QuoteStatus status) {
        return new QuoteRequestResponse(id, UUID.randomUUID(), UUID.randomUUID(), null,
            status, "123 Main", null, "City", null, "12345", "US",
            null, null, null, null, null, null, null, null, List.of(), Instant.now(), Instant.now());
    }

    private QuoteItemResponse stubItem(UUID id) {
        return new QuoteItemResponse(id, null, "Widget", 1, new BigDecimal("99.00"), Instant.now());
    }

    @Test
    void listQuotes_returns200() throws Exception {
        PagedResult<QuoteRequestResponse> result = new PagedResult<>(
            List.of(stubQuote(UUID.randomUUID(), QuoteStatus.PENDING)),
            PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(quoteService.listAll(any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/admin/quotes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getQuote_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(quoteService.getAdmin(id)).thenReturn(stubQuote(id, QuoteStatus.PENDING));

        mvc.perform(get("/api/v1/admin/quotes/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void getQuote_notFound_returns404() throws Exception {
        when(quoteService.getAdmin(any()))
            .thenThrow(new NotFoundException("QUOTE_NOT_FOUND", "Not found"));

        mvc.perform(get("/api/v1/admin/quotes/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void assignQuote_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        when(quoteService.assign(any(), any())).thenReturn(stubQuote(id, QuoteStatus.ASSIGNED));

        mvc.perform(post("/api/v1/admin/quotes/{id}/assign", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AssignStaffRequest(staffId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ASSIGNED"));
    }

    @Test
    void updateItem_returns200() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(quoteService.updateItem(any(), any(), any())).thenReturn(stubItem(itemId));

        mvc.perform(put("/api/v1/admin/quotes/{id}/items/{itemId}", quoteId, itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateQuoteItemRequest(2, new BigDecimal("150.00")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unitPrice").value(99.00));
    }

    @Test
    void addItem_returns200() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(quoteService.addItem(any(), any())).thenReturn(stubItem(itemId));

        mvc.perform(post("/api/v1/admin/quotes/{id}/items", quoteId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new AddQuoteItemRequest("Freight charge", 1, new BigDecimal("75.00")))))
            .andExpect(status().isOk());
    }

    @Test
    void removeItem_returns204() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        doNothing().when(quoteService).removeItem(any(), any());

        mvc.perform(delete("/api/v1/admin/quotes/{id}/items/{itemId}", quoteId, itemId))
            .andExpect(status().isNoContent());
    }

    @Test
    void updateNotes_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(quoteService.updateNotes(any(), any())).thenReturn(stubQuote(id, QuoteStatus.DRAFT));

        mvc.perform(put("/api/v1/admin/quotes/{id}/notes", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateStaffNotesRequest("Internal note"))))
            .andExpect(status().isOk());
    }

    @Test
    void sendQuote_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(quoteService.send(any(), any())).thenReturn(stubQuote(id, QuoteStatus.SENT));

        mvc.perform(post("/api/v1/admin/quotes/{id}/send", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new SendQuoteRequest(Instant.now().plus(7, ChronoUnit.DAYS)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SENT"));
    }

    @Test
    void sendQuote_missingPrices_returns400() throws Exception {
        when(quoteService.send(any(), any()))
            .thenThrow(new ValidationException("MISSING_UNIT_PRICE", "All items need prices"));

        mvc.perform(post("/api/v1/admin/quotes/{id}/send", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new SendQuoteRequest(Instant.now().plus(7, ChronoUnit.DAYS)))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void cancelQuote_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(quoteService.cancel(any())).thenReturn(stubQuote(id, QuoteStatus.CANCELLED));

        mvc.perform(post("/api/v1/admin/quotes/{id}/cancel", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelQuote_alreadyAccepted_returns409() throws Exception {
        when(quoteService.cancel(any()))
            .thenThrow(new ConflictException("INVALID_QUOTE_STATUS", "Cannot cancel"));

        mvc.perform(post("/api/v1/admin/quotes/{id}/cancel", UUID.randomUUID()))
            .andExpect(status().isConflict());
    }
}
