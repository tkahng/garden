package io.k2dv.garden.admin.iam.controller;

import io.k2dv.garden.admin.iam.dto.*;
import io.k2dv.garden.admin.iam.service.AdminIamService;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: IAM", description = "Admin roles and permissions")
@RestController
@RequestMapping("/api/v1/admin/iam")
@RequiredArgsConstructor
@HasPermission("iam:manage")
public class AdminIamController {

    private final AdminIamService adminIamService;

    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> listRoles() {
        return ApiResponse.of(adminIamService.listRoles());
    }

    @PostMapping("/roles")
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest req) {
        return ApiResponse.of(adminIamService.createRole(req));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest req) {
        return ApiResponse.of(adminIamService.updateRole(id, req));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        adminIamService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        return ApiResponse.of(adminIamService.listPermissions());
    }

    @PostMapping("/roles/{id}/permissions")
    public ApiResponse<RoleResponse> assignPermission(
            @PathVariable UUID id,
            @Valid @RequestBody AssignPermissionRequest req) {
        return ApiResponse.of(adminIamService.assignPermission(id, req));
    }

    @DeleteMapping("/roles/{id}/permissions/{permissionId}")
    public ResponseEntity<Void> removePermission(
            @PathVariable UUID id,
            @PathVariable UUID permissionId) {
        adminIamService.removePermission(id, permissionId);
        return ResponseEntity.noContent().build();
    }
}
