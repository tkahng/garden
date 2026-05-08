package io.k2dv.garden.order.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.fulfillment.dto.FulfillmentResponse;
import io.k2dv.garden.fulfillment.service.FulfillmentService;
import io.k2dv.garden.order.dto.OrderFilter;
import io.k2dv.garden.order.dto.OrderResponse;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.payment.dto.CheckoutResponse;
import io.k2dv.garden.payment.service.PaymentService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Tag(name = "Storefront Orders", description = "User order history and self-service actions")
@RestController
@RequestMapping("/api/v1/storefront/orders")
@RequiredArgsConstructor
@Authenticated
public class StorefrontOrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private final PaymentService paymentService;
    private final CompanyService companyService;
    private final FulfillmentService fulfillmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<OrderResponse>>> listOrders(
            @CurrentUser User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        PagedResult<OrderResponse> result = orderService.list(
            new OrderFilter(null, user.getId(), null, null),
            PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @CurrentUser User user,
            @PathVariable UUID id) {
        OrderResponse order = orderService.getOrderResponse(id);
        if (!Objects.equals(order.userId(), user.getId())) {
            throw new ValidationException("ORDER_NOT_OWNED", "Order does not belong to current user");
        }
        return ResponseEntity.ok(ApiResponse.of(order));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @CurrentUser User user,
            @PathVariable UUID id) {
        OrderResponse order = orderService.getOrderResponse(id);
        if (!Objects.equals(order.userId(), user.getId())) {
            throw new ValidationException("ORDER_NOT_OWNED", "Order does not belong to current user");
        }
        return ResponseEntity.ok(ApiResponse.of(orderService.cancelAndReturn(id)));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<OrderResponse>> refundOrder(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.refundOrder(id, user.getId())));
    }

    @PostMapping("/{id}/reorder")
    public ResponseEntity<ApiResponse<CartResponse>> reorder(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(cartService.reorderFromHistory(user.getId(), id)));
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<ApiResponse<PagedResult<OrderResponse>>> listPendingApprovals(
            @CurrentUser User user,
            @RequestParam UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        companyService.requireMemberAccess(companyId, user.getId());
        if (!companyService.isOwnerOrManager(companyId, user.getId())) {
            throw new ForbiddenException("INSUFFICIENT_COMPANY_ROLE",
                "Only a company owner or manager can view pending approvals");
        }
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.of(
            orderService.listPendingApprovals(companyId,
                PageRequest.of(page, clampedSize, Sort.by("createdAt").descending()))));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<CheckoutResponse>> approveOrder(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.approveCartOrder(id, user.getId())));
    }

    @PostMapping("/{id}/reject-approval")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectApproval(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(orderService.rejectApproval(id, user.getId())));
    }

    @GetMapping("/{id}/fulfillments")
    public ResponseEntity<ApiResponse<List<FulfillmentResponse>>> listFulfillments(
            @CurrentUser User user,
            @PathVariable UUID id) {
        OrderResponse order = orderService.getOrderResponse(id);
        if (!Objects.equals(order.userId(), user.getId())) {
            throw new ValidationException("ORDER_NOT_OWNED", "Order does not belong to current user");
        }
        return ResponseEntity.ok(ApiResponse.of(fulfillmentService.list(id)));
    }
}
