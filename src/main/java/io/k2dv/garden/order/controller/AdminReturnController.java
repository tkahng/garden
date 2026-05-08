package io.k2dv.garden.order.controller;

import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.order.dto.ReturnRequestResponse;
import io.k2dv.garden.order.dto.ReviewReturnRequest;
import io.k2dv.garden.order.model.ReturnRequestStatus;
import io.k2dv.garden.order.service.ReturnRequestService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin Returns", description = "Admin return request management")
@RestController
@RequestMapping("/api/v1/admin/returns")
@RequiredArgsConstructor
public class AdminReturnController {

    private final ReturnRequestService returnService;

    @GetMapping
    @HasPermission("return:read")
    public ResponseEntity<ApiResponse<PagedResult<ReturnRequestResponse>>> list(
            @RequestParam(required = false) ReturnRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.of(
            returnService.listAll(status,
                PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @GetMapping("/{id}")
    @HasPermission("return:read")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(returnService.getById(id)));
    }

    @PostMapping("/{id}/approve")
    @HasPermission("return:write")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> approve(
            @CurrentUser User user,
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewReturnRequest req) {
        return ResponseEntity.ok(ApiResponse.of(returnService.approve(id, user.getId(), req)));
    }

    @PostMapping("/{id}/reject")
    @HasPermission("return:write")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> reject(
            @CurrentUser User user,
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewReturnRequest req) {
        return ResponseEntity.ok(ApiResponse.of(returnService.reject(id, user.getId(), req)));
    }

    @PostMapping("/{id}/complete")
    @HasPermission("return:write")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> complete(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(returnService.complete(id, user.getId())));
    }
}
