package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.b2b.dto.CreditAccountResponse;
import io.k2dv.garden.b2b.dto.CreateCreditAccountRequest;
import io.k2dv.garden.b2b.dto.UpdateCreditAccountRequest;
import io.k2dv.garden.b2b.service.CreditAccountService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin: Credit Accounts", description = "Net terms credit management")
@RestController
@RequestMapping("/api/v1/admin/credit-accounts")
@RequiredArgsConstructor
public class AdminCreditAccountController {

    private final CreditAccountService creditAccountService;

    @PostMapping
    @HasPermission("credit_account:write")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> create(
        @Valid @RequestBody CreateCreditAccountRequest req) {
        return ResponseEntity.ok(ApiResponse.of(creditAccountService.create(req)));
    }

    @GetMapping("/company/{companyId}")
    @HasPermission("credit_account:read")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> getByCompany(
        @PathVariable UUID companyId) {
        return ResponseEntity.ok(ApiResponse.of(creditAccountService.getByCompany(companyId)));
    }

    @PutMapping("/company/{companyId}")
    @HasPermission("credit_account:write")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> update(
        @PathVariable UUID companyId,
        @Valid @RequestBody UpdateCreditAccountRequest req) {
        return ResponseEntity.ok(ApiResponse.of(creditAccountService.update(companyId, req)));
    }

    @DeleteMapping("/company/{companyId}")
    @HasPermission("credit_account:write")
    public ResponseEntity<Void> delete(@PathVariable UUID companyId) {
        creditAccountService.delete(companyId);
        return ResponseEntity.noContent().build();
    }
}
