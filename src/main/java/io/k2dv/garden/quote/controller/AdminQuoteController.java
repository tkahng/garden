package io.k2dv.garden.quote.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.quote.dto.*;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.quote.service.QuoteService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin Quotes", description = "Admin quote management")
@RestController
@RequestMapping("/api/v1/admin/quotes")
@RequiredArgsConstructor
public class AdminQuoteController {

    private final QuoteService quoteService;

    @GetMapping
    @HasPermission("quote:read")
    public ResponseEntity<ApiResponse<PagedResult<QuoteRequestResponse>>> list(
        @RequestParam(required = false) QuoteStatus status,
        @RequestParam(required = false) UUID companyId,
        @RequestParam(required = false) UUID assignedStaffId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        QuoteFilter filter = new QuoteFilter(status, companyId, assignedStaffId, null);
        return ResponseEntity.ok(ApiResponse.of(
            quoteService.listAll(filter, PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    @HasPermission("quote:read")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.getAdmin(id)));
    }

    @PostMapping("/{id}/assign")
    @HasPermission("quote:write")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> assign(
        @PathVariable UUID id,
        @Valid @RequestBody AssignStaffRequest req) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.assign(id, req)));
    }

    @PutMapping("/{id}/items/{itemId}")
    @HasPermission("quote:write")
    public ResponseEntity<ApiResponse<QuoteItemResponse>> updateItem(
        @PathVariable UUID id,
        @PathVariable UUID itemId,
        @Valid @RequestBody UpdateQuoteItemRequest req) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.updateItem(id, itemId, req)));
    }

    @PostMapping("/{id}/items")
    @HasPermission("quote:write")
    public ResponseEntity<ApiResponse<QuoteItemResponse>> addItem(
        @PathVariable UUID id,
        @Valid @RequestBody AddQuoteItemRequest req) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.addItem(id, req)));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @HasPermission("quote:write")
    public ResponseEntity<Void> removeItem(
        @PathVariable UUID id,
        @PathVariable UUID itemId) {
        quoteService.removeItem(id, itemId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/notes")
    @HasPermission("quote:write")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> updateNotes(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateStaffNotesRequest req) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.updateNotes(id, req)));
    }

    @PostMapping("/{id}/send")
    @HasPermission("quote:write")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> send(
        @PathVariable UUID id,
        @Valid @RequestBody SendQuoteRequest req) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.send(id, req)));
    }

    @PostMapping("/{id}/cancel")
    @HasPermission("quote:write")
    public ResponseEntity<ApiResponse<QuoteRequestResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.cancel(id)));
    }
}
