package io.k2dv.garden.admin.user.controller;

import io.k2dv.garden.admin.user.dto.AdminUserResponse;
import io.k2dv.garden.admin.user.dto.AssignRoleRequest;
import io.k2dv.garden.admin.user.dto.UpdateUserRequest;
import io.k2dv.garden.admin.user.dto.UserFilter;
import io.k2dv.garden.admin.user.service.AdminUserService;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.user.model.UserStatus;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin: Users", description = "Admin user management")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @HasPermission("user:read")
    public ApiResponse<PagedResult<AdminUserResponse>> listUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ApiResponse.of(adminUserService.listUsers(new UserFilter(status, email), PageRequest.of(page, clampedSize)));
    }

    @GetMapping("/{id}")
    @HasPermission("user:read")
    public ApiResponse<AdminUserResponse> getUser(@PathVariable UUID id) {
        return ApiResponse.of(adminUserService.getUser(id));
    }

    @PutMapping("/{id}")
    @HasPermission("user:write")
    public ApiResponse<AdminUserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest req) {
        return ApiResponse.of(adminUserService.updateUser(id, req));
    }

    @PutMapping("/{id}/suspend")
    @HasPermission("staff:manage")
    public ResponseEntity<Void> suspendUser(@PathVariable UUID id) {
        adminUserService.suspendUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivate")
    @HasPermission("staff:manage")
    public ResponseEntity<Void> reactivateUser(@PathVariable UUID id) {
        adminUserService.reactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/roles")
    @HasPermission("iam:manage")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRoleRequest req) {
        adminUserService.assignRole(id, req.roleName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/roles/{roleName}")
    @HasPermission("iam:manage")
    public ResponseEntity<Void> removeRole(
            @PathVariable UUID id,
            @PathVariable String roleName) {
        adminUserService.removeRole(id, roleName);
        return ResponseEntity.noContent().build();
    }
}
