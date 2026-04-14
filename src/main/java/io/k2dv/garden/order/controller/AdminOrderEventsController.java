package io.k2dv.garden.order.controller;

import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.order.dto.CreateOrderNoteRequest;
import io.k2dv.garden.order.dto.OrderEventResponse;
import io.k2dv.garden.order.model.OrderEventType;
import io.k2dv.garden.order.service.OrderEventService;
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

@Tag(name = "Admin: Order Events", description = "Order timeline and notes")
@RestController
@RequestMapping("/api/v1/admin/orders/{orderId}/events")
@RequiredArgsConstructor
public class AdminOrderEventsController {

    private final OrderEventService orderEventService;

    @GetMapping
    @HasPermission("order:read")
    public ApiResponse<List<OrderEventResponse>> list(@PathVariable UUID orderId) {
        return ApiResponse.of(orderEventService.list(orderId));
    }

    @PostMapping
    @HasPermission("order:write")
    public ResponseEntity<ApiResponse<OrderEventResponse>> addNote(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateOrderNoteRequest req,
            @CurrentUser User admin) {
        String name = admin.getFirstName() + " " + admin.getLastName();
        OrderEventResponse event = orderEventService.emit(
            orderId, OrderEventType.NOTE_ADDED, req.message(), admin.getId(), name, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(event));
    }
}
