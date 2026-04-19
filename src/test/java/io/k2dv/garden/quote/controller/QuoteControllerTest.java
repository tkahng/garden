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
import static org.hamcrest.Matchers.containsString;

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
            null, null, null, null, null, null, null, null, List.of(), Instant.now(), Instant.now());
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
            .thenReturn(new QuoteAcceptResponse("https://checkout.stripe.com/pay/cs_test", UUID.randomUUID(), false, null));

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
            null, null, null, null, null, null, null, null, List.of(), Instant.now(), Instant.now());
        when(quoteService.reject(any(), any())).thenReturn(rejected);

        mvc.perform(post("/api/v1/quotes/{id}/reject", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void cancelQuote_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        QuoteRequestResponse cancelled = new QuoteRequestResponse(id, UUID.randomUUID(), UUID.randomUUID(),
            null, QuoteStatus.CANCELLED, "123 Main", null, "City", null, "12345", "US",
            null, null, null, null, null, null, null, null, List.of(), Instant.now(), Instant.now());
        when(quoteService.cancelForUser(any(), any())).thenReturn(cancelled);

        mvc.perform(post("/api/v1/quotes/{id}/cancel", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelQuote_alreadyAccepted_returns409() throws Exception {
        when(quoteService.cancelForUser(any(), any()))
            .thenThrow(new ConflictException("INVALID_QUOTE_STATUS", "Cannot cancel accepted quote"));

        mvc.perform(post("/api/v1/quotes/{id}/cancel", UUID.randomUUID()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("INVALID_QUOTE_STATUS"));
    }

    @Test
    void cancelQuote_wrongOwner_returns403() throws Exception {
        when(quoteService.cancelForUser(any(), any()))
            .thenThrow(new io.k2dv.garden.shared.exception.ForbiddenException("NOT_YOUR_QUOTE", "Not yours"));

        mvc.perform(post("/api/v1/quotes/{id}/cancel", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    void downloadPdf_returnsPdfBytes() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] pdfBytes = "%PDF-1.4 fake content".getBytes();
        when(quoteService.downloadPdf(any(), any())).thenReturn(pdfBytes);

        mvc.perform(get("/api/v1/quotes/{id}/pdf", id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Content-Disposition", containsString(id.toString())));
    }

    @Test
    void downloadPdf_notGenerated_returns404() throws Exception {
        when(quoteService.downloadPdf(any(), any()))
            .thenThrow(new NotFoundException("PDF_NOT_AVAILABLE", "Quote PDF has not been generated yet"));

        mvc.perform(get("/api/v1/quotes/{id}/pdf", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("PDF_NOT_AVAILABLE"));
    }

    @Test
    void downloadPdf_wrongOwner_returns403() throws Exception {
        when(quoteService.downloadPdf(any(), any()))
            .thenThrow(new io.k2dv.garden.shared.exception.ForbiddenException("NOT_YOUR_QUOTE", "This quote does not belong to you"));

        mvc.perform(get("/api/v1/quotes/{id}/pdf", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    void listPendingApprovals_returns200() throws Exception {
        io.k2dv.garden.shared.dto.PagedResult<QuoteRequestResponse> result =
            new io.k2dv.garden.shared.dto.PagedResult<>(
                List.of(stubQuote(UUID.randomUUID())),
                PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(quoteService.listPendingApprovals(any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/quotes/pending-approvals"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void approveSpend_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(quoteService.approveSpend(any(), any()))
            .thenReturn(new QuoteAcceptResponse("https://checkout.stripe.com/pay/cs_test", UUID.randomUUID(), false, null));

        mvc.perform(post("/api/v1/quotes/{id}/approve", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pendingApproval").value(false));
    }

    @Test
    void approveSpend_notOwner_returns403() throws Exception {
        when(quoteService.approveSpend(any(), any()))
            .thenThrow(new io.k2dv.garden.shared.exception.ForbiddenException("NOT_COMPANY_OWNER", "Only a company owner can approve spend"));

        mvc.perform(post("/api/v1/quotes/{id}/approve", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    void approveSpend_wrongStatus_returns409() throws Exception {
        when(quoteService.approveSpend(any(), any()))
            .thenThrow(new ConflictException("INVALID_QUOTE_STATUS", "Quote must be in PENDING_APPROVAL status"));

        mvc.perform(post("/api/v1/quotes/{id}/approve", UUID.randomUUID()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("INVALID_QUOTE_STATUS"));
    }

    @Test
    void rejectApproval_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        QuoteRequestResponse rejected = new QuoteRequestResponse(id, UUID.randomUUID(), UUID.randomUUID(),
            null, QuoteStatus.REJECTED, "123 Main", null, "City", null, "12345", "US",
            null, null, null, null, null, null, null, null, List.of(), Instant.now(), Instant.now());
        when(quoteService.rejectSpend(any(), any())).thenReturn(rejected);

        mvc.perform(post("/api/v1/quotes/{id}/reject-approval", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void rejectApproval_notOwner_returns403() throws Exception {
        when(quoteService.rejectSpend(any(), any()))
            .thenThrow(new io.k2dv.garden.shared.exception.ForbiddenException("NOT_COMPANY_OWNER", "Only a company owner can reject spend"));

        mvc.perform(post("/api/v1/quotes/{id}/reject-approval", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }
}
