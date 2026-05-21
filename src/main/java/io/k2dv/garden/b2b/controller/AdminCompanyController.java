package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.b2b.dto.AddCatalogProductRequest;
import io.k2dv.garden.b2b.dto.AdminUpdateCompanyRequest;
import io.k2dv.garden.b2b.dto.CompanyResponse;
import io.k2dv.garden.b2b.dto.CompanySpendingSummaryResponse;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.MetadataRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@Tag(name = "Admin: Companies", description = "Admin company management")
@RestController
@RequestMapping("/api/v1/admin/companies")
@RequiredArgsConstructor
public class AdminCompanyController {

    private final CompanyService companyService;

    @GetMapping
    @HasPermission("company:read")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(companyService.listAll()));
    }

    @GetMapping("/{id}")
    @HasPermission("company:read")
    public ResponseEntity<ApiResponse<CompanyResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(companyService.adminGetById(id)));
    }

    @PutMapping("/{id}")
    @HasPermission("company:write")
    public ResponseEntity<ApiResponse<CompanyResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateCompanyRequest req) {
        return ResponseEntity.ok(ApiResponse.of(companyService.adminUpdate(id, req)));
    }

    @PutMapping("/{id}/metadata")
    @HasPermission("company:write")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateMetadata(
            @PathVariable UUID id,
            @Valid @RequestBody MetadataRequest req) {
        return ResponseEntity.ok(ApiResponse.of(companyService.updateMetadata(id, req.metadata())));
    }

    // ─── Catalog ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/catalog")
    @HasPermission("company:read")
    public ResponseEntity<ApiResponse<List<UUID>>> getCatalog(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(companyService.getCatalogProductIds(id)));
    }

    @PostMapping("/{id}/catalog")
    @HasPermission("company:write")
    public ResponseEntity<Void> addToCatalog(
            @PathVariable UUID id,
            @Valid @RequestBody AddCatalogProductRequest req) {
        companyService.addToCatalog(id, req.productId());
        return ResponseEntity.status(NO_CONTENT).build();
    }

    @DeleteMapping("/{id}/catalog/{productId}")
    @HasPermission("company:write")
    public ResponseEntity<Void> removeFromCatalog(
            @PathVariable UUID id,
            @PathVariable UUID productId) {
        companyService.removeFromCatalog(id, productId);
        return ResponseEntity.status(NO_CONTENT).build();
    }

    @GetMapping("/{id}/spending-summary")
    @HasPermission("company:read")
    public ResponseEntity<ApiResponse<CompanySpendingSummaryResponse>> spendingSummary(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(companyService.getSpendingSummary(id)));
    }
}
