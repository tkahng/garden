package io.k2dv.garden.order.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.order.dto.OrderFilter;
import io.k2dv.garden.order.dto.OrderResponse;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

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

    @GetMapping("/{id}")
    @HasPermission("order:read")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.getOrderResponse(id)));
    }

    @PutMapping("/{id}/cancel")
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable UUID id) {
        orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.of(orderService.getOrderResponse(id)));
    }
}
