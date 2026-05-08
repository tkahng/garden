package io.k2dv.garden.order.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.order.dto.ReturnRequestResponse;
import io.k2dv.garden.order.dto.SubmitReturnRequest;
import io.k2dv.garden.order.service.ReturnRequestService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Storefront Returns", description = "Customer return request management")
@RestController
@RequestMapping("/api/v1/storefront/returns")
@RequiredArgsConstructor
@Authenticated
public class StorefrontReturnController {

    private final ReturnRequestService returnService;

    @PostMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> submit(
            @CurrentUser User user,
            @PathVariable UUID orderId,
            @Valid @RequestBody SubmitReturnRequest req) {
        return ResponseEntity.ok(ApiResponse.of(returnService.submit(orderId, user.getId(), req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<ReturnRequestResponse>>> list(
            @CurrentUser User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.of(
            returnService.listForUser(user.getId(),
                PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> get(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(returnService.getByIdForUser(id, user.getId())));
    }
}
