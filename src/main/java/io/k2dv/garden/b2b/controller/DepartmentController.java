package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.b2b.dto.AssignDepartmentRequest;
import io.k2dv.garden.b2b.dto.DepartmentRequest;
import io.k2dv.garden.b2b.dto.DepartmentResponse;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.b2b.service.DepartmentService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Departments", description = "Company department management")
@RestController
@RequestMapping("/api/v1/companies/{companyId}/departments")
@RequiredArgsConstructor
@Authenticated
public class DepartmentController {

    private final DepartmentService service;
    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> tree(
        @CurrentUser User user,
        @PathVariable UUID companyId) {
        requireMember(companyId, user.getId());
        return ResponseEntity.ok(ApiResponse.of(service.tree(companyId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
        @CurrentUser User user,
        @PathVariable UUID companyId,
        @Valid @RequestBody DepartmentRequest req) {
        requireOwnerOrManager(companyId, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(companyId, req)));
    }

    @PutMapping("/{deptId}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
        @CurrentUser User user,
        @PathVariable UUID companyId,
        @PathVariable UUID deptId,
        @Valid @RequestBody DepartmentRequest req) {
        requireOwnerOrManager(companyId, user.getId());
        return ResponseEntity.ok(ApiResponse.of(service.rename(deptId, companyId, req)));
    }

    @DeleteMapping("/{deptId}")
    public ResponseEntity<Void> delete(
        @CurrentUser User user,
        @PathVariable UUID companyId,
        @PathVariable UUID deptId) {
        requireOwnerOrManager(companyId, user.getId());
        service.delete(deptId, companyId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/members/{userId}/department")
    public ResponseEntity<Void> assignDepartment(
        @CurrentUser User user,
        @PathVariable UUID companyId,
        @PathVariable UUID userId,
        @RequestBody AssignDepartmentRequest req) {
        requireOwnerOrManager(companyId, user.getId());
        service.assignMemberDepartment(companyId, userId, req);
        return ResponseEntity.noContent().build();
    }

    private void requireOwnerOrManager(UUID companyId, UUID userId) {
        if (!companyService.isOwnerOrManager(companyId, userId)) {
            throw new ForbiddenException("INSUFFICIENT_COMPANY_ROLE",
                "Only company owners and managers can manage departments");
        }
    }

    private void requireMember(UUID companyId, UUID userId) {
        if (!companyService.isMember(companyId, userId)) {
            throw new ForbiddenException("NOT_A_MEMBER",
                "You are not a member of this company");
        }
    }
}
