package io.k2dv.garden.b2b.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.b2b.dto.InvoiceResponse;
import io.k2dv.garden.b2b.dto.RecordPaymentRequest;
import io.k2dv.garden.b2b.model.InvoiceStatus;
import io.k2dv.garden.b2b.service.InvoicePdfService;
import io.k2dv.garden.b2b.service.InvoiceService;
import io.k2dv.garden.config.TestSecurityConfig;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminInvoiceController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminInvoiceControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean InvoiceService invoiceService;
    @MockitoBean InvoicePdfService invoicePdfService;

    private InvoiceResponse stubInvoice(UUID id, UUID companyId, InvoiceStatus status) {
        return new InvoiceResponse(id, companyId, UUID.randomUUID(), null,
            status, new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"),
            "USD", Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS),
            List.of(), Instant.now(), Instant.now());
    }

    @Test
    void list_returns200WithPagedResult() throws Exception {
        UUID companyId = UUID.randomUUID();
        PagedResult<InvoiceResponse> result = new PagedResult<>(
            List.of(stubInvoice(UUID.randomUUID(), companyId, InvoiceStatus.ISSUED)),
            PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(invoiceService.listAll(isNull(), isNull(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/admin/invoices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void list_withFilters_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        PagedResult<InvoiceResponse> result = new PagedResult<>(
            List.of(stubInvoice(UUID.randomUUID(), companyId, InvoiceStatus.OVERDUE)),
            PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(invoiceService.listAll(eq(companyId), eq(InvoiceStatus.OVERDUE), any())).thenReturn(result);

        mvc.perform(get("/api/v1/admin/invoices")
                .param("companyId", companyId.toString())
                .param("status", "OVERDUE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void get_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(invoiceService.getById(eq(id))).thenReturn(stubInvoice(id, companyId, InvoiceStatus.ISSUED));

        mvc.perform(get("/api/v1/admin/invoices/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(id.toString()))
            .andExpect(jsonPath("$.data.status").value("ISSUED"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(invoiceService.getById(any()))
            .thenThrow(new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found"));

        mvc.perform(get("/api/v1/admin/invoices/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("INVOICE_NOT_FOUND"));
    }

    @Test
    void recordPayment_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.recordPayment(eq(id), any()))
            .thenReturn(stubInvoice(id, UUID.randomUUID(), InvoiceStatus.PARTIAL));

        mvc.perform(post("/api/v1/admin/invoices/{id}/payments", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RecordPaymentRequest(new BigDecimal("200.00"), null, "REF-001", null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PARTIAL"));
    }

    @Test
    void recordPayment_onPaid_returns409() throws Exception {
        when(invoiceService.recordPayment(any(), any()))
            .thenThrow(new ConflictException("INVOICE_NOT_PAYABLE", "Cannot record payment on a PAID invoice"));

        mvc.perform(post("/api/v1/admin/invoices/{id}/payments", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RecordPaymentRequest(new BigDecimal("1.00"), null, null, null, null))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("INVOICE_NOT_PAYABLE"));
    }

    @Test
    void markOverdue_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.markOverdue(eq(id)))
            .thenReturn(stubInvoice(id, UUID.randomUUID(), InvoiceStatus.OVERDUE));

        mvc.perform(post("/api/v1/admin/invoices/{id}/overdue", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("OVERDUE"));
    }

    @Test
    void voidInvoice_returns200WithVoidStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceService.voidInvoice(eq(id)))
            .thenReturn(stubInvoice(id, UUID.randomUUID(), InvoiceStatus.VOID));

        mvc.perform(delete("/api/v1/admin/invoices/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("VOID"));
    }

    @Test
    void voidInvoice_alreadyPaid_returns409() throws Exception {
        when(invoiceService.voidInvoice(any()))
            .thenThrow(new ConflictException("INVOICE_ALREADY_PAID", "Cannot void a paid invoice"));

        mvc.perform(delete("/api/v1/admin/invoices/{id}", UUID.randomUUID()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("INVOICE_ALREADY_PAID"));
    }

    @Test
    void downloadPdf_returns200WithPdfBytes() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] fakePdf = "%PDF-1.4 fake content".getBytes();
        when(invoicePdfService.generate(eq(id))).thenReturn(fakePdf);

        mvc.perform(get("/api/v1/admin/invoices/{id}/pdf", id))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"invoice-" + id + ".pdf\""))
            .andExpect(content().contentType("application/pdf"))
            .andExpect(content().bytes(fakePdf));
    }

    @Test
    void downloadPdf_notFound_returns404() throws Exception {
        when(invoicePdfService.generate(any()))
            .thenThrow(new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found"));

        mvc.perform(get("/api/v1/admin/invoices/{id}/pdf", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("INVOICE_NOT_FOUND"));
    }
}
