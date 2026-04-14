package io.k2dv.garden.fulfillment.controller;

import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.fulfillment.dto.CreateFulfillmentRequest;
import io.k2dv.garden.fulfillment.dto.FulfillmentResponse;
import io.k2dv.garden.fulfillment.dto.UpdateFulfillmentRequest;
import io.k2dv.garden.fulfillment.service.FulfillmentService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: Fulfillments", description = "Order fulfillment management")
@RestController
@RequestMapping("/api/v1/admin/orders/{orderId}/fulfillments")
@RequiredArgsConstructor
public class AdminFulfillmentController {

    private final FulfillmentService fulfillmentService;

    @GetMapping
    @HasPermission("order:read")
    public ApiResponse<List<FulfillmentResponse>> list(@PathVariable UUID orderId) {
        return ApiResponse.of(fulfillmentService.list(orderId));
    }

    @GetMapping("/{fulfillmentId}")
    @HasPermission("order:read")
    public ApiResponse<FulfillmentResponse> getById(
            @PathVariable UUID orderId,
            @PathVariable UUID fulfillmentId) {
        return ApiResponse.of(fulfillmentService.getById(orderId, fulfillmentId));
    }

    @PostMapping
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<FulfillmentResponse>> create(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateFulfillmentRequest req,
            @CurrentUser User admin) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(fulfillmentService.create(orderId, req, admin)));
    }

    @PutMapping("/{fulfillmentId}")
    @HasPermission("order:write")
    public ApiResponse<FulfillmentResponse> update(
            @PathVariable UUID orderId,
            @PathVariable UUID fulfillmentId,
            @RequestBody UpdateFulfillmentRequest req) {
        return ApiResponse.of(fulfillmentService.update(orderId, fulfillmentId, req));
    }
}
