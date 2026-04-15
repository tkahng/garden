package io.k2dv.garden.fulfillment.service;

import io.k2dv.garden.fulfillment.dto.CreateFulfillmentRequest;
import io.k2dv.garden.fulfillment.dto.FulfillmentItemResponse;
import io.k2dv.garden.fulfillment.dto.FulfillmentResponse;
import io.k2dv.garden.fulfillment.dto.UpdateFulfillmentRequest;
import io.k2dv.garden.fulfillment.model.Fulfillment;
import io.k2dv.garden.fulfillment.model.FulfillmentItem;
import io.k2dv.garden.fulfillment.model.FulfillmentStatus;
import io.k2dv.garden.fulfillment.repository.FulfillmentItemRepository;
import io.k2dv.garden.fulfillment.repository.FulfillmentRepository;
import io.k2dv.garden.order.model.OrderEventType;
import io.k2dv.garden.order.model.OrderItem;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderItemRepository;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.order.service.OrderEventService;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.k2dv.garden.order.model.Order;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private final FulfillmentRepository fulfillmentRepo;
    private final FulfillmentItemRepository fulfillmentItemRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final OrderEventService orderEventService;

    @Transactional
    public FulfillmentResponse create(UUID orderId, CreateFulfillmentRequest req, User admin) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.REFUNDED) {
            throw new ConflictException("INVALID_ORDER_STATUS",
                "Cannot create fulfillment for order in status: " + order.getStatus());
        }

        // Validate each requested item belongs to this order and won't exceed ordered quantity
        Map<UUID, OrderItem> orderItemsById = orderItemRepo.findByOrderId(orderId).stream()
            .collect(Collectors.toMap(OrderItem::getId, oi -> oi));

        Map<UUID, Integer> alreadyFulfilledQty = fulfillmentItemRepo.findActiveItemsByOrderId(orderId).stream()
            .collect(Collectors.groupingBy(
                FulfillmentItem::getOrderItemId,
                Collectors.summingInt(FulfillmentItem::getQuantity)));

        for (var itemReq : req.items()) {
            OrderItem orderItem = orderItemsById.get(itemReq.orderItemId());
            if (orderItem == null) {
                throw new ValidationException("ORDER_ITEM_NOT_FOUND",
                    "Order item " + itemReq.orderItemId() + " does not belong to order " + orderId);
            }
            int alreadyFulfilled = alreadyFulfilledQty.getOrDefault(itemReq.orderItemId(), 0);
            if (alreadyFulfilled + itemReq.quantity() > orderItem.getQuantity()) {
                throw new ConflictException("OVER_FULFILLMENT",
                    "Cannot fulfill " + itemReq.quantity() + " units of item " + itemReq.orderItemId()
                    + ". Ordered: " + orderItem.getQuantity()
                    + ", already fulfilled: " + alreadyFulfilled);
            }
        }

        Fulfillment f = new Fulfillment();
        f.setOrderId(orderId);
        f.setTrackingNumber(req.trackingNumber());
        f.setTrackingCompany(req.trackingCompany());
        f.setTrackingUrl(req.trackingUrl());
        f.setNote(req.note());
        f = fulfillmentRepo.save(f);

        for (var itemReq : req.items()) {
            FulfillmentItem fi = new FulfillmentItem();
            fi.setFulfillmentId(f.getId());
            fi.setOrderItemId(itemReq.orderItemId());
            fi.setQuantity(itemReq.quantity());
            fulfillmentItemRepo.save(fi);
        }

        recalculateOrderStatus(orderId);

        String adminName = admin.getFirstName() + " " + admin.getLastName();
        orderEventService.emit(orderId, OrderEventType.FULFILLMENT_CREATED,
            "Fulfillment created", admin.getId(), adminName, null);

        return toResponse(f);
    }

    @Transactional
    public FulfillmentResponse update(UUID orderId, UUID fulfillmentId, UpdateFulfillmentRequest req) {
        Fulfillment f = fulfillmentRepo.findByIdAndOrderId(fulfillmentId, orderId)
            .orElseThrow(() -> new NotFoundException("FULFILLMENT_NOT_FOUND", "Fulfillment not found"));

        if (req.status() != null) {
            if (!isValidTransition(f.getStatus(), req.status())) {
                throw new ConflictException("INVALID_STATUS_TRANSITION",
                    "Cannot transition fulfillment from " + f.getStatus() + " to " + req.status());
            }
            f.setStatus(req.status());
        }
        if (req.trackingNumber() != null) f.setTrackingNumber(req.trackingNumber());
        if (req.trackingCompany() != null) f.setTrackingCompany(req.trackingCompany());
        if (req.trackingUrl() != null) f.setTrackingUrl(req.trackingUrl());
        if (req.note() != null) f.setNote(req.note());
        f = fulfillmentRepo.save(f);

        recalculateOrderStatus(orderId);
        orderEventService.emit(orderId, OrderEventType.FULFILLMENT_UPDATED,
            "Fulfillment updated", null, "admin", null);

        return toResponse(f);
    }

    @Transactional(readOnly = true)
    public List<FulfillmentResponse> list(UUID orderId) {
        return fulfillmentRepo.findByOrderId(orderId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public FulfillmentResponse getById(UUID orderId, UUID fulfillmentId) {
        Fulfillment f = fulfillmentRepo.findByIdAndOrderId(fulfillmentId, orderId)
            .orElseThrow(() -> new NotFoundException("FULFILLMENT_NOT_FOUND", "Fulfillment not found"));
        return toResponse(f);
    }

    private void recalculateOrderStatus(UUID orderId) {
        List<OrderItem> orderItems = orderItemRepo.findByOrderId(orderId);
        if (orderItems.isEmpty()) return;

        List<FulfillmentItem> activeItems = fulfillmentItemRepo.findActiveItemsByOrderId(orderId);

        Map<UUID, Integer> fulfilledQtyByOrderItemId = activeItems.stream()
            .collect(Collectors.groupingBy(
                FulfillmentItem::getOrderItemId,
                Collectors.summingInt(FulfillmentItem::getQuantity)));

        long fullyFulfilled = orderItems.stream()
            .filter(oi -> fulfilledQtyByOrderItemId.getOrDefault(oi.getId(), 0) >= oi.getQuantity())
            .count();
        long anyFulfilled = orderItems.stream()
            .filter(oi -> fulfilledQtyByOrderItemId.getOrDefault(oi.getId(), 0) > 0)
            .count();

        OrderStatus newStatus;
        if (fullyFulfilled == orderItems.size()) {
            newStatus = OrderStatus.FULFILLED;
        } else if (anyFulfilled > 0) {
            newStatus = OrderStatus.PARTIALLY_FULFILLED;
        } else {
            newStatus = OrderStatus.PAID;
        }

        orderRepo.findById(orderId).ifPresent(order -> {
            order.setStatus(newStatus);
            orderRepo.save(order);
        });
    }

    private boolean isValidTransition(FulfillmentStatus from, FulfillmentStatus to) {
        if (from == to) return true;
        return switch (from) {
            case PENDING -> to == FulfillmentStatus.SHIPPED || to == FulfillmentStatus.CANCELLED;
            case SHIPPED -> to == FulfillmentStatus.DELIVERED || to == FulfillmentStatus.CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    private FulfillmentResponse toResponse(Fulfillment f) {
        List<FulfillmentItemResponse> items = fulfillmentItemRepo.findByFulfillmentId(f.getId())
            .stream().map(FulfillmentItemResponse::from).toList();
        return FulfillmentResponse.from(f, items);
    }
}
