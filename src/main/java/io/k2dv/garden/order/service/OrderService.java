package io.k2dv.garden.order.service;

import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.inventory.service.InventoryService;
import io.k2dv.garden.order.dto.OrderFilter;
import io.k2dv.garden.order.dto.OrderItemResponse;
import io.k2dv.garden.order.dto.OrderResponse;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderItem;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderItemRepository;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;
    private final InventoryService inventoryService;

    @Transactional
    public Order createFromCart(UUID userId, List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            throw new ValidationException("EMPTY_CART", "Cart is empty");
        }

        for (CartItem cartItem : cartItems) {
            ProductVariant variant = variantRepo.findByIdAndDeletedAtIsNull(cartItem.getVariantId())
                .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND",
                    "Variant not found: " + cartItem.getVariantId()));
            Product product = productRepo.findByIdAndDeletedAtIsNull(variant.getProductId())
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND",
                    "Product not found for variant: " + cartItem.getVariantId()));
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ValidationException("PRODUCT_NOT_ACTIVE",
                    "Product is not active: " + product.getTitle());
            }
        }

        // Reserve inventory for each item (throws ValidationException if insufficient)
        for (CartItem cartItem : cartItems) {
            inventoryService.reserveStock(cartItem.getVariantId(), cartItem.getQuantity());
        }

        BigDecimal total = cartItems.stream()
            .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setUserId(userId);
        order.setTotalAmount(total);
        order = orderRepo.save(order);

        for (CartItem cartItem : cartItems) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setVariantId(cartItem.getVariantId());
            item.setQuantity(cartItem.getQuantity());
            item.setUnitPrice(cartItem.getUnitPrice());
            orderItemRepo.save(item);
        }

        return order;
    }

    @Transactional
    public void setStripeSession(UUID orderId, String stripeSessionId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        order.setStripeSessionId(stripeSessionId);
        orderRepo.save(order);
    }

    @Transactional
    public void confirmPayment(String stripeSessionId, String stripePaymentIntentId) {
        orderRepo.findByStripeSessionId(stripeSessionId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PAID) return; // idempotent
            order.setStripePaymentIntentId(stripePaymentIntentId);
            order.setStatus(OrderStatus.PAID);
            orderRepo.save(order);

            orderItemRepo.findByOrderId(order.getId())
                .forEach(item -> inventoryService.confirmSale(item.getVariantId(), item.getQuantity()));
        });
    }

    @Transactional
    public void cancelBySession(String stripeSessionId) {
        orderRepo.findByStripeSessionId(stripeSessionId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.CANCELLED) return; // idempotent
            order.setStatus(OrderStatus.CANCELLED);
            orderRepo.save(order);

            orderItemRepo.findByOrderId(order.getId())
                .forEach(item -> inventoryService.releaseReservation(item.getVariantId(), item.getQuantity()));
        });
    }

    @Transactional
    public void cancelOrder(UUID orderId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (order.getStatus() == OrderStatus.CANCELLED) return;
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("INVALID_ORDER_STATUS",
                "Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepo.save(order);

        orderItemRepo.findByOrderId(orderId)
            .forEach(item -> inventoryService.releaseReservation(item.getVariantId(), item.getQuantity()));
    }

    @Transactional
    public OrderResponse cancelAndReturn(UUID orderId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("INVALID_ORDER_STATUS",
                "Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepo.save(order);
        orderItemRepo.findByOrderId(orderId)
            .forEach(item -> inventoryService.releaseReservation(item.getVariantId(), item.getQuantity()));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Order findByStripeSessionId(String stripeSessionId) {
        return orderRepo.findByStripeSessionId(stripeSessionId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND",
                "Order not found for session: " + stripeSessionId));
    }

    @Transactional(readOnly = true)
    public Order getById(UUID orderId) {
        return orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderResponse(UUID orderId) {
        Order order = getById(orderId);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> list(OrderFilter filter, Pageable pageable) {
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) predicates.add(cb.equal(root.get("status"), filter.status()));
            if (filter.userId() != null) predicates.add(cb.equal(root.get("userId"), filter.userId()));
            if (filter.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            if (filter.to() != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PagedResult.of(orderRepo.findAll(spec, pageable), this::toResponse);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = orderItemRepo.findByOrderId(order.getId()).stream()
            .map(i -> new OrderItemResponse(i.getId(), i.getVariantId(), i.getQuantity(), i.getUnitPrice()))
            .toList();
        return new OrderResponse(order.getId(), order.getUserId(), order.getStatus(),
            order.getTotalAmount(), order.getCurrency(), order.getStripeSessionId(),
            items, order.getCreatedAt());
    }
}
