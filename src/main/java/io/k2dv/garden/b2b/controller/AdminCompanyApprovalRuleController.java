package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.b2b.dto.CompanyApprovalRuleRequest;
import io.k2dv.garden.b2b.dto.CompanyApprovalRuleResponse;
import io.k2dv.garden.b2b.service.CompanyApprovalRuleService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: Approval Rules", description = "Company approval rule management")
@RestController
@RequestMapping("/api/v1/admin/companies/{companyId}/approval-rules")
@RequiredArgsConstructor
public class AdminCompanyApprovalRuleController {

    private final CompanyApprovalRuleService service;

    @GetMapping
    @HasPermission("company:read")
    public ResponseEntity<ApiResponse<List<CompanyApprovalRuleResponse>>> list(
        @PathVariable UUID companyId) {
        return ResponseEntity.ok(ApiResponse.of(service.listByCompany(companyId)));
    }

    @PostMapping
    @HasPermission("company:write")
    public ResponseEntity<ApiResponse<CompanyApprovalRuleResponse>> create(
        @PathVariable UUID companyId,
        @Valid @RequestBody CompanyApprovalRuleRequest req) {
        return ResponseEntity.ok(ApiResponse.of(service.create(companyId, req)));
    }

    @PutMapping("/{ruleId}")
    @HasPermission("company:write")
    public ResponseEntity<ApiResponse<CompanyApprovalRuleResponse>> update(
        @PathVariable UUID companyId,
        @PathVariable UUID ruleId,
        @Valid @RequestBody CompanyApprovalRuleRequest req) {
        return ResponseEntity.ok(ApiResponse.of(service.update(ruleId, companyId, req)));
    }

    @DeleteMapping("/{ruleId}")
    @HasPermission("company:write")
    public ResponseEntity<Void> delete(
        @PathVariable UUID companyId,
        @PathVariable UUID ruleId) {
        service.delete(ruleId, companyId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{ruleId}/toggle")
    @HasPermission("company:write")
    public ResponseEntity<Void> toggle(
        @PathVariable UUID companyId,
        @PathVariable UUID ruleId,
        @RequestParam boolean active) {
        service.toggleActive(ruleId, companyId, active);
        return ResponseEntity.noContent().build();
    }
}
