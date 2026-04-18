package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.service.PriceListService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: Price Lists", description = "Contract-based pricing management")
@RestController
@RequestMapping("/api/v1/admin/price-lists")
@RequiredArgsConstructor
public class AdminPriceListController {

    private final PriceListService priceListService;

    @PostMapping
    @HasPermission("price_list:write")
    public ResponseEntity<ApiResponse<PriceListResponse>> create(
        @Valid @RequestBody CreatePriceListRequest req) {
        return ResponseEntity.ok(ApiResponse.of(priceListService.create(req)));
    }

    @GetMapping
    @HasPermission("price_list:read")
    public ResponseEntity<ApiResponse<List<PriceListResponse>>> listByCompany(
        @RequestParam UUID companyId) {
        return ResponseEntity.ok(ApiResponse.of(priceListService.listByCompany(companyId)));
    }

    @GetMapping("/{id}")
    @HasPermission("price_list:read")
    public ResponseEntity<ApiResponse<PriceListResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(priceListService.getById(id)));
    }

    @PutMapping("/{id}")
    @HasPermission("price_list:write")
    public ResponseEntity<ApiResponse<PriceListResponse>> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdatePriceListRequest req) {
        return ResponseEntity.ok(ApiResponse.of(priceListService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("price_list:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        priceListService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/entries")
    @HasPermission("price_list:read")
    public ResponseEntity<ApiResponse<List<PriceListEntryResponse>>> listEntries(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(priceListService.listEntries(id)));
    }

    @PutMapping("/{id}/entries/{variantId}")
    @HasPermission("price_list:write")
    public ResponseEntity<ApiResponse<PriceListEntryResponse>> upsertEntry(
        @PathVariable UUID id,
        @PathVariable UUID variantId,
        @Valid @RequestBody UpsertPriceListEntryRequest req) {
        return ResponseEntity.ok(ApiResponse.of(priceListService.upsertEntry(id, variantId, req)));
    }

    @DeleteMapping("/{id}/entries/{variantId}")
    @HasPermission("price_list:delete")
    public ResponseEntity<Void> deleteEntry(
        @PathVariable UUID id,
        @PathVariable UUID variantId) {
        priceListService.deleteEntry(id, variantId);
        return ResponseEntity.noContent().build();
    }
}
