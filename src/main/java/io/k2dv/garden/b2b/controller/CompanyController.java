package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.service.CompanyInvitationService;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.b2b.service.CreditAccountService;
import io.k2dv.garden.b2b.service.InvoiceService;
import io.k2dv.garden.b2b.service.PriceListService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Company", description = "B2B company management")
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Authenticated
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyInvitationService invitationService;
    private final PriceListService priceListService;
    private final InvoiceService invoiceService;
    private final CreditAccountService creditAccountService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> create(
        @CurrentUser User user,
        @Valid @RequestBody CreateCompanyRequest req) {
        return ResponseEntity.ok(ApiResponse.of(companyService.create(user.getId(), req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> list(@CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse.of(companyService.listForUser(user.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> get(
        @CurrentUser User user,
        @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(companyService.getById(id, user.getId())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> update(
        @CurrentUser User user,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCompanyRequest req) {
        return ResponseEntity.ok(ApiResponse.of(companyService.update(id, user.getId(), req)));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<CompanyMemberResponse>>> listMembers(
        @CurrentUser User user,
        @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(companyService.listMembers(id, user.getId())));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<CompanyMemberResponse>> addMember(
        @CurrentUser User user,
        @PathVariable UUID id,
        @Valid @RequestBody AddMemberRequest req) {
        return ResponseEntity.ok(ApiResponse.of(companyService.addMember(id, user.getId(), req)));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
        @CurrentUser User user,
        @PathVariable UUID id,
        @PathVariable UUID userId) {
        companyService.removeMember(id, user.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members/{userId}/role")
    public ResponseEntity<ApiResponse<CompanyMemberResponse>> updateMemberRole(
        @CurrentUser User user,
        @PathVariable UUID id,
        @PathVariable UUID userId,
        @Valid @RequestBody UpdateMemberRoleRequest req) {
        return ResponseEntity.ok(ApiResponse.of(
            companyService.updateMemberRole(id, userId, user.getId(), req)));
    }

    @PutMapping("/{id}/members/{userId}/spending-limit")
    public ResponseEntity<ApiResponse<CompanyMemberResponse>> updateSpendingLimit(
        @CurrentUser User user,
        @PathVariable UUID id,
        @PathVariable UUID userId,
        @Valid @RequestBody UpdateSpendingLimitRequest req) {
        return ResponseEntity.ok(ApiResponse.of(
            companyService.updateSpendingLimit(id, userId, user.getId(), req)));
    }

    @GetMapping("/{id}/invitations")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> listInvitations(
        @CurrentUser User user,
        @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(invitationService.listPending(id, user.getId())));
    }

    @PostMapping("/{id}/invitations")
    public ResponseEntity<ApiResponse<InvitationResponse>> invite(
        @CurrentUser User user,
        @PathVariable UUID id,
        @Valid @RequestBody CreateInvitationRequest req) {
        return ResponseEntity.ok(ApiResponse.of(invitationService.invite(id, user.getId(), req)));
    }

    @DeleteMapping("/{id}/invitations/{invitationId}")
    public ResponseEntity<ApiResponse<InvitationResponse>> cancelInvitation(
        @CurrentUser User user,
        @PathVariable UUID id,
        @PathVariable UUID invitationId) {
        return ResponseEntity.ok(ApiResponse.of(invitationService.cancel(id, invitationId, user.getId())));
    }

    @GetMapping("/{id}/invoices")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> listInvoices(
        @CurrentUser User user,
        @PathVariable UUID id) {
        companyService.requireMemberAccess(id, user.getId());
        return ResponseEntity.ok(ApiResponse.of(invoiceService.listByCompany(id)));
    }

    @GetMapping("/{id}/price-lists")
    public ResponseEntity<ApiResponse<List<PriceListResponse>>> listPriceLists(
        @CurrentUser User user,
        @PathVariable UUID id) {
        companyService.requireMemberAccess(id, user.getId());
        return ResponseEntity.ok(ApiResponse.of(priceListService.listByCompany(id)));
    }

    @GetMapping("/{id}/price-lists/{priceListId}/entries")
    public ResponseEntity<ApiResponse<List<CustomerPriceEntryResponse>>> listPriceListEntries(
        @CurrentUser User user,
        @PathVariable UUID id,
        @PathVariable UUID priceListId) {
        companyService.requireMemberAccess(id, user.getId());
        return ResponseEntity.ok(ApiResponse.of(priceListService.listEntriesForCustomer(priceListId, id)));
    }

    @GetMapping("/{id}/price")
    public ResponseEntity<ApiResponse<ResolvedPriceResponse>> resolvePrice(
        @CurrentUser User user,
        @PathVariable UUID id,
        @RequestParam UUID variantId,
        @RequestParam(defaultValue = "1") int qty) {
        companyService.requireMemberAccess(id, user.getId());
        return ResponseEntity.ok(ApiResponse.of(priceListService.resolvePrice(id, variantId, qty)));
    }

    @GetMapping("/{id}/credit-account")
    public ResponseEntity<ApiResponse<CreditAccountResponse>> getCreditAccount(
        @CurrentUser User user,
        @PathVariable UUID id) {
        companyService.requireMemberAccess(id, user.getId());
        return ResponseEntity.ok(ApiResponse.of(creditAccountService.getByCompany(id)));
    }

    @GetMapping("/{id}/statement")
    public ResponseEntity<String> downloadStatement(
        @CurrentUser User user,
        @PathVariable UUID id,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to) {
        companyService.requireMemberAccess(id, user.getId());
        String csv = invoiceService.generateStatementCsv(id, from, to);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"statement-" + id + ".csv\"")
            .body(csv);
    }
}
