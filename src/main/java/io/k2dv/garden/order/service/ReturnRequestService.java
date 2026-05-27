package io.k2dv.garden.order.service;

import com.stripe.exception.StripeException;
import com.stripe.param.RefundCreateParams;
import io.k2dv.garden.order.dto.*;
import io.k2dv.garden.order.model.*;
import io.k2dv.garden.order.repository.*;
import io.k2dv.garden.payment.exception.PaymentException;
import io.k2dv.garden.payment.gateway.StripeGateway;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the full lifecycle of customer return requests from submission through staff review
 * and final completion. When a return is approved with a REFUND resolution the Stripe refund
 * is issued automatically; all state transitions are recorded on the order's event timeline.
 */
@Service
@RequiredArgsConstructor
public class ReturnRequestService {

    private final ReturnRequestRepository returnRepo;
    private final ReturnRequestItemRepository returnItemRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final OrderEventService eventService;
    private final StripeGateway stripeGateway;

    private static final Set<OrderStatus> RETURNABLE_STATUSES =
        Set.of(OrderStatus.PAID, OrderStatus.PARTIALLY_FULFILLED, OrderStatus.FULFILLED);

    private static final Set<ReturnRequestStatus> OPEN_STATUSES =
        Set.of(ReturnRequestStatus.PENDING, ReturnRequestStatus.APPROVED);

    /**
     * Opens a new return request against a paid or fulfilled order; rejects the request if another
     * open return already exists or the order is not in a returnable state. Defaults the resolution
     * to REFUND when the caller does not specify one.
     */
    @Transactional
    public ReturnRequestResponse submit(UUID orderId, UUID userId, SubmitReturnRequest req) {
        Order order = requireOrder(orderId);
        if (!RETURNABLE_STATUSES.contains(order.getStatus())) {
            throw new ConflictException("INVALID_ORDER_STATUS",
                "Returns can only be submitted for paid or fulfilled orders");
        }
        if (userId != null && !userId.equals(order.getUserId())) {
            throw new ValidationException("ORDER_NOT_OWNED", "Order does not belong to current user");
        }
        if (returnRepo.existsByOrderIdAndStatusIn(orderId, List.copyOf(OPEN_STATUSES))) {
            throw new ConflictException("RETURN_ALREADY_OPEN",
                "An open return request already exists for this order");
        }

        ReturnRequest rr = new ReturnRequest();
        rr.setOrderId(orderId);
        rr.setUserId(userId);
        rr.setReason(req.reason());
        rr.setNotes(req.notes());
        rr.setResolution(req.resolution() != null ? req.resolution() : ReturnResolution.REFUND);
        rr = returnRepo.save(rr);

        List<ReturnRequestItem> savedItems = saveItems(rr.getId(), req.items(), orderId);

        eventService.emit(orderId, OrderEventType.RETURN_REQUESTED,
            "Return requested: " + req.reason(), userId, "customer", null);

        return toResponse(rr, savedItems);
    }

    /**
     * Returns a paginated list of return requests belonging to the authenticated user,
     * ordered by the repository default (typically newest first).
     */
    @Transactional(readOnly = true)
    public PagedResult<ReturnRequestResponse> listForUser(UUID userId, Pageable pageable) {
        return PagedResult.of(
            returnRepo.findByUserId(userId, pageable),
            rr -> toResponse(rr, returnItemRepo.findByReturnRequestId(rr.getId())));
    }

    /**
     * Returns a paginated, admin-facing list of all return requests; optionally filtered to a
     * single status to support the staff review queue.
     */
    @Transactional(readOnly = true)
    public PagedResult<ReturnRequestResponse> listAll(ReturnRequestStatus status, Pageable pageable) {
        var page = status != null
            ? returnRepo.findByStatus(status, pageable)
            : returnRepo.findAll(pageable);
        return PagedResult.of(page,
            rr -> toResponse(rr, returnItemRepo.findByReturnRequestId(rr.getId())));
    }

    /**
     * Retrieves the full details of a return request by its ID; intended for admin use where
     * no ownership check is required.
     */
    @Transactional(readOnly = true)
    public ReturnRequestResponse getById(UUID id) {
        ReturnRequest rr = requireReturnRequest(id);
        return toResponse(rr, returnItemRepo.findByReturnRequestId(id));
    }

    /**
     * Retrieves a return request for the authenticated customer, throwing a not-found error (rather
     * than forbidden) if the request belongs to a different user to avoid ID enumeration.
     */
    @Transactional(readOnly = true)
    public ReturnRequestResponse getByIdForUser(UUID id, UUID userId) {
        ReturnRequest rr = requireReturnRequest(id);
        if (userId != null && !userId.equals(rr.getUserId())) {
            throw new NotFoundException("RETURN_NOT_FOUND", "Return request not found");
        }
        return toResponse(rr, returnItemRepo.findByReturnRequestId(id));
    }

    /**
     * Staff approval of a pending return request; if the resolution is REFUND, the Stripe refund
     * is issued atomically within the same transaction before the status is updated.
     */
    @Transactional
    public ReturnRequestResponse approve(UUID id, UUID staffId, ReviewReturnRequest req) {
        ReturnRequest rr = requireReturnRequest(id);
        if (rr.getStatus() != ReturnRequestStatus.PENDING) {
            throw new ConflictException("INVALID_RETURN_STATUS",
                "Only PENDING return requests can be approved");
        }

        if (rr.getResolution() == ReturnResolution.REFUND) {
            issueStripeRefund(rr.getOrderId());
        }

        rr.setStatus(ReturnRequestStatus.APPROVED);
        rr.setStaffNotes(req != null ? req.staffNotes() : null);
        rr.setResolvedBy(staffId);
        rr.setResolvedAt(Instant.now());
        rr = returnRepo.save(rr);

        eventService.emit(rr.getOrderId(), OrderEventType.RETURN_APPROVED,
            "Return request approved", staffId, "admin", null);

        return toResponse(rr, returnItemRepo.findByReturnRequestId(id));
    }

    /**
     * Staff rejection of a pending return request; no Stripe refund is issued. The request moves
     * to REJECTED and no further transitions are permitted.
     */
    @Transactional
    public ReturnRequestResponse reject(UUID id, UUID staffId, ReviewReturnRequest req) {
        ReturnRequest rr = requireReturnRequest(id);
        if (rr.getStatus() != ReturnRequestStatus.PENDING) {
            throw new ConflictException("INVALID_RETURN_STATUS",
                "Only PENDING return requests can be rejected");
        }

        rr.setStatus(ReturnRequestStatus.REJECTED);
        rr.setStaffNotes(req != null ? req.staffNotes() : null);
        rr.setResolvedBy(staffId);
        rr.setResolvedAt(Instant.now());
        rr = returnRepo.save(rr);

        eventService.emit(rr.getOrderId(), OrderEventType.RETURN_REJECTED,
            "Return request rejected", staffId, "admin", null);

        return toResponse(rr, returnItemRepo.findByReturnRequestId(id));
    }

    /**
     * Marks an approved return request as physically completed (e.g., item received back in
     * warehouse); this is the terminal success state for the return workflow.
     */
    @Transactional
    public ReturnRequestResponse complete(UUID id, UUID staffId) {
        ReturnRequest rr = requireReturnRequest(id);
        if (rr.getStatus() != ReturnRequestStatus.APPROVED) {
            throw new ConflictException("INVALID_RETURN_STATUS",
                "Only APPROVED return requests can be completed");
        }

        rr.setStatus(ReturnRequestStatus.COMPLETED);
        rr = returnRepo.save(rr);

        eventService.emit(rr.getOrderId(), OrderEventType.RETURN_COMPLETED,
            "Return completed", staffId, "admin", null);

        return toResponse(rr, returnItemRepo.findByReturnRequestId(id));
    }

    private List<ReturnRequestItem> saveItems(UUID returnRequestId,
                                               List<ReturnRequestItemInput> inputs,
                                               UUID orderId) {
        if (inputs == null || inputs.isEmpty()) return List.of();

        List<UUID> validOrderItemIds = orderItemRepo.findByOrderId(orderId)
            .stream().map(OrderItem::getId).toList();

        return inputs.stream().map(input -> {
            if (!validOrderItemIds.contains(input.orderItemId())) {
                throw new ValidationException("INVALID_ORDER_ITEM",
                    "Order item " + input.orderItemId() + " does not belong to this order");
            }
            ReturnRequestItem item = new ReturnRequestItem();
            item.setReturnRequestId(returnRequestId);
            item.setOrderItemId(input.orderItemId());
            item.setQuantity(input.quantity());
            return returnItemRepo.save(item);
        }).toList();
    }

    private void issueStripeRefund(UUID orderId) {
        Order order = requireOrder(orderId);
        if (order.getStripePaymentIntentId() == null) return;
        try {
            stripeGateway.createRefund(RefundCreateParams.builder()
                .setPaymentIntent(order.getStripePaymentIntentId())
                .build());
        } catch (StripeException e) {
            throw new PaymentException("STRIPE_REFUND_ERROR", "Failed to issue refund: " + e.getMessage());
        }
    }

    private ReturnRequest requireReturnRequest(UUID id) {
        return returnRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return request not found"));
    }

    private Order requireOrder(UUID orderId) {
        return orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
    }

    private ReturnRequestResponse toResponse(ReturnRequest rr, List<ReturnRequestItem> items) {
        List<ReturnRequestItemResponse> itemResponses = items.stream()
            .map(i -> new ReturnRequestItemResponse(i.getId(), i.getOrderItemId(), i.getQuantity()))
            .toList();
        return new ReturnRequestResponse(
            rr.getId(), rr.getOrderId(), rr.getUserId(),
            rr.getReason(), rr.getNotes(), rr.getResolution(),
            rr.getStatus(), rr.getStaffNotes(),
            rr.getResolvedBy(), rr.getResolvedAt(),
            itemResponses, rr.getCreatedAt(), rr.getUpdatedAt()
        );
    }
}
