package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.b2b.dto.CreateInvoiceFromOrderRequest;
import io.k2dv.garden.b2b.dto.InvoiceResponse;
import io.k2dv.garden.b2b.dto.RecordPaymentRequest;
import io.k2dv.garden.b2b.model.InvoiceStatus;
import io.k2dv.garden.b2b.service.InvoicePdfService;
import io.k2dv.garden.b2b.service.InvoiceService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin: Invoices", description = "Invoice management and payment recording")
@RestController
@RequestMapping("/api/v1/admin/invoices")
@RequiredArgsConstructor
public class AdminInvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    @PostMapping("/from-order/{orderId}")
    @HasPermission("invoice:write")
    public ResponseEntity<ApiResponse<InvoiceResponse>> createFromOrder(
        @PathVariable UUID orderId,
        @Valid @RequestBody CreateInvoiceFromOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.of(
            invoiceService.createManualInvoice(orderId, req.companyId(), req.paymentTermsDays())));
    }

    @GetMapping
    @HasPermission("invoice:read")
    public ResponseEntity<ApiResponse<PagedResult<InvoiceResponse>>> list(
        @RequestParam(required = false) UUID companyId,
        @RequestParam(required = false) InvoiceStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(
            invoiceService.listAll(companyId, status, PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    @HasPermission("invoice:read")
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(invoiceService.getById(id)));
    }

    @PostMapping("/{id}/payments")
    @HasPermission("invoice:write")
    public ResponseEntity<ApiResponse<InvoiceResponse>> recordPayment(
        @PathVariable UUID id,
        @Valid @RequestBody RecordPaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.of(invoiceService.recordPayment(id, req)));
    }

    @PostMapping("/{id}/overdue")
    @HasPermission("invoice:write")
    public ResponseEntity<ApiResponse<InvoiceResponse>> markOverdue(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(invoiceService.markOverdue(id)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("invoice:delete")
    public ResponseEntity<ApiResponse<InvoiceResponse>> voidInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(invoiceService.voidInvoice(id)));
    }

    @GetMapping(value = "/{id}/pdf", produces = "application/pdf")
    @HasPermission("invoice:read")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        byte[] pdf = invoicePdfService.generate(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
