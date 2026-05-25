package io.k2dv.garden.quote.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.quote.dto.*;
import io.k2dv.garden.quote.service.QuoteService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Quotes", description = "Quote request operations for customers")
@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
@Authenticated
public class QuoteController {

    private final QuoteService quoteService;

    @PostMapping
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> submit(
        @CurrentUser User user,
        @Valid @RequestBody SubmitQuoteRequest req) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.submit(user.getId(), req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<QuoteRequestResponse>>> list(
        @CurrentUser User user,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(
            quoteService.listForUser(user.getId(), PageRequest.of(page, size))));
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<ApiResponse<PagedResult<QuoteRequestResponse>>> listPendingApprovals(
        @CurrentUser User user,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(
            quoteService.listPendingApprovals(user.getId(), PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> get(
        @CurrentUser User user,
        @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.getForUser(id, user.getId())));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<QuoteAcceptResponse>> accept(
        @CurrentUser User user,
        @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.accept(id, user.getId())));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> reject(
        @CurrentUser User user,
        @PathVariable UUID id,
        @RequestBody(required = false) RejectQuoteRequest body) {
        String reason = body != null ? body.reason() : null;
        return ResponseEntity.ok(ApiResponse.of(quoteService.reject(id, user.getId(), reason)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> cancel(
        @CurrentUser User user,
        @PathVariable UUID id,
        @RequestBody(required = false) RejectQuoteRequest body) {
        String reason = body != null ? body.reason() : null;
        return ResponseEntity.ok(ApiResponse.of(quoteService.cancelForUser(id, user.getId(), reason)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<QuoteAcceptResponse>> approveSpend(
        @CurrentUser User user,
        @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.approveSpend(id, user.getId())));
    }

    @PostMapping("/{id}/reject-approval")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> rejectSpend(
        @CurrentUser User user,
        @PathVariable UUID id,
        @RequestBody(required = false) RejectQuoteRequest body) {
        String reason = body != null ? body.reason() : null;
        return ResponseEntity.ok(ApiResponse.of(quoteService.rejectSpend(id, user.getId(), reason)));
    }

    @GetMapping("/{id}/pendencies")
    public ResponseEntity<ApiResponse<java.util.List<io.k2dv.garden.b2b.dto.QuoteApprovalPendencyResponse>>> getPendencies(
        @CurrentUser User user,
        @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.listPendencies(id, user.getId())));
    }

    @PostMapping("/{id}/pendencies/{ruleId}/approve")
    public ResponseEntity<ApiResponse<QuoteAcceptResponse>> approvePendency(
        @CurrentUser User user,
        @PathVariable UUID id,
        @PathVariable UUID ruleId) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.approvePendency(id, user.getId(), ruleId)));
    }

    @PostMapping("/{id}/pendencies/{ruleId}/reject")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> rejectPendency(
        @CurrentUser User user,
        @PathVariable UUID id,
        @PathVariable UUID ruleId,
        @RequestBody(required = false) RejectQuoteRequest body) {
        String reason = body != null ? body.reason() : null;
        return ResponseEntity.ok(ApiResponse.of(quoteService.rejectPendency(id, user.getId(), ruleId, reason)));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
        @CurrentUser User user,
        @PathVariable UUID id) {
        byte[] pdf = quoteService.downloadPdf(id, user.getId());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quote-" + id + ".pdf\"")
            .body(pdf);
    }
}
