package io.k2dv.garden.order.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.order.dto.CreateDraftOrderRequest;
import io.k2dv.garden.order.dto.DraftOrderItemRequest;
import io.k2dv.garden.order.dto.OrderFilter;
import io.k2dv.garden.order.dto.OrderResponse;
import io.k2dv.garden.order.dto.UpdateOrderRequest;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.BulkIdsRequest;
import io.k2dv.garden.shared.dto.MetadataRequest;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Orders", description = "Admin order management")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @HasPermission("order:read")
    public ResponseEntity<ApiResponse<PagedResult<OrderResponse>>> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResult<OrderResponse> result = orderService.list(
            new OrderFilter(status, userId, from, to),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    @GetMapping("/export")
    @HasPermission("order:read")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        String csv = orderService.exportCsv(new OrderFilter(status, userId, from, to));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orders.csv\"")
            .body(csv);
    }

    @GetMapping("/{id}")
    @HasPermission("order:read")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.getOrderResponse(id)));
    }

    @PutMapping("/{id}/cancel")
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.cancelAndReturn(id)));
    }

    @PostMapping("/{id}/refund")
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<OrderResponse>> refundOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.adminRefundOrder(id)));
    }

    @PutMapping("/{id}")
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrder(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.of(orderService.updateOrder(id, req)));
    }

    @PostMapping("/draft")
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<OrderResponse>> createDraft(
            @Valid @RequestBody CreateDraftOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(orderService.createDraft(req)));
    }

    @PutMapping("/{id}/draft/items")
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<OrderResponse>> updateDraftItems(
            @PathVariable UUID id,
            @Valid @RequestBody List<DraftOrderItemRequest> items) {
        return ResponseEntity.ok(ApiResponse.of(orderService.updateDraftItems(id, items)));
    }

    @PostMapping("/{id}/draft/complete")
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<OrderResponse>> completeDraft(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.completeDraft(id)));
    }

    @PutMapping("/{id}/metadata")
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<OrderResponse>> updateMetadata(
            @PathVariable UUID id, @Valid @RequestBody MetadataRequest req) {
        return ResponseEntity.ok(ApiResponse.of(orderService.updateMetadata(id, req.metadata())));
    }

    @PostMapping("/bulk/cancel")
    @HasPermission("order:write")
    public ResponseEntity<Void> bulkCancel(@Valid @RequestBody BulkIdsRequest req) {
        orderService.bulkCancel(req.ids());
        return ResponseEntity.noContent().build();
    }
}
