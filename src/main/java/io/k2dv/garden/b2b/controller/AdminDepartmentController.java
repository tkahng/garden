package io.k2dv.garden.b2b.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.b2b.dto.AssignDepartmentRequest;
import io.k2dv.garden.b2b.dto.DepartmentRequest;
import io.k2dv.garden.b2b.dto.DepartmentResponse;
import io.k2dv.garden.b2b.service.DepartmentService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: Departments", description = "Admin company department management")
@RestController
@RequestMapping("/api/v1/admin/companies/{companyId}/departments")
@RequiredArgsConstructor
public class AdminDepartmentController {

    private final DepartmentService service;

    @GetMapping
    @HasPermission("company:read")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> tree(
        @PathVariable UUID companyId) {
        return ResponseEntity.ok(ApiResponse.of(service.tree(companyId)));
    }

    @PostMapping
    @HasPermission("company:write")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
        @PathVariable UUID companyId,
        @Valid @RequestBody DepartmentRequest req) {
        return ResponseEntity.ok(ApiResponse.of(service.create(companyId, req)));
    }

    @PutMapping("/{deptId}")
    @HasPermission("company:write")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
        @PathVariable UUID companyId,
        @PathVariable UUID deptId,
        @Valid @RequestBody DepartmentRequest req) {
        return ResponseEntity.ok(ApiResponse.of(service.rename(deptId, companyId, req)));
    }

    @DeleteMapping("/{deptId}")
    @HasPermission("company:write")
    public ResponseEntity<Void> delete(
        @PathVariable UUID companyId,
        @PathVariable UUID deptId) {
        service.delete(deptId, companyId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/members/{userId}/department")
    @HasPermission("company:write")
    public ResponseEntity<Void> assignDepartment(
        @PathVariable UUID companyId,
        @PathVariable UUID userId,
        @RequestBody AssignDepartmentRequest req) {
        service.assignMemberDepartment(companyId, userId, req);
        return ResponseEntity.noContent().build();
    }
}
