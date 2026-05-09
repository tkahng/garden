package io.k2dv.garden.audit.controller;

import io.k2dv.garden.audit.dto.AuditLogResponse;
import io.k2dv.garden.audit.service.AuditLogService;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin: Audit Log", description = "Admin audit trail")
@RestController
@RequestMapping("/api/v1/admin/audit-log")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @HasPermission("audit:read")
    public ApiResponse<PagedResult<AuditLogResponse>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int clampedSize = Math.min(size, 100);
        return ApiResponse.of(auditLogService.list(
            entityType, entityId, actorEmail,
            PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}
