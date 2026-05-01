package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.b2b.dto.AdminUpdateCompanyRequest;
import io.k2dv.garden.b2b.dto.CompanyResponse;
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
}
