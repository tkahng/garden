package io.k2dv.garden.order.service;

import com.stripe.exception.StripeException;
import com.stripe.param.RefundCreateParams;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.quote.model.QuoteItem;
import io.k2dv.garden.quote.model.QuoteRequest;
import io.k2dv.garden.inventory.service.InventoryService;
import io.k2dv.garden.order.dto.CreateDraftOrderRequest;
import io.k2dv.garden.order.dto.OrderEventResponse;
import io.k2dv.garden.order.dto.OrderFilter;
import io.k2dv.garden.order.dto.OrderItemProductInfo;
import io.k2dv.garden.order.dto.OrderItemResponse;
import io.k2dv.garden.order.dto.OrderResponse;
import io.k2dv.garden.order.dto.UpdateOrderRequest;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderEvent;
import io.k2dv.garden.order.model.OrderEventType;
import io.k2dv.garden.order.model.OrderItem;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderItemRepository;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.payment.exception.PaymentException;
import io.k2dv.garden.payment.gateway.StripeGateway;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.product.service.ProductImageResolver;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.automation.AutoTagService;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final AutoTagService autoTagService;
    private final ProductImageResolver imageResolver;
    private final InventoryService inventoryService;
    private final StripeGateway stripeGateway;
    private final OrderEventService orderEventService;

    @Transactional
    public Order createFromCart(UUID userId, List<CartItem> cartItems) {
        return createFromCart(userId, cartItems, null, null, null, null);
    }

    @Transactional
    public Order createFromCart(UUID userId, List<CartItem> cartItems,
                                UUID shippingRateId, BigDecimal shippingCost, String shippingAddress) {
        return createFromCart(userId, cartItems, shippingRateId, shippingCost, shippingAddress, null);
    }

    @Transactional
    public Order createFromCart(UUID userId, List<CartItem> cartItems,
                                UUID shippingRateId, BigDecimal shippingCost, String shippingAddress,
                                String poNumber) {
        return buildOrder(userId, null, null, false, cartItems, shippingRateId, shippingCost, shippingAddress, poNumber);
    }

    @Transactional
    public Order createFromCart(UUID userId, UUID companyId, boolean taxExempt, List<CartItem> cartItems,
                                UUID shippingRateId, BigDecimal shippingCost, String shippingAddress,
                                String poNumber) {
        return buildOrder(userId, null, companyId, taxExempt, cartItems, shippingRateId, shippingCost, shippingAddress, poNumber);
    }

    @Transactional
    public Order createGuestOrder(String guestEmail, List<CartItem> cartItems,
                                  UUID shippingRateId, BigDecimal shippingCost, String shippingAddress) {
        return createGuestOrder(guestEmail, cartItems, shippingRateId, shippingCost, shippingAddress, null);
    }

    @Transactional
    public Order createGuestOrder(String guestEmail, List<CartItem> cartItems,
                                  UUID shippingRateId, BigDecimal shippingCost, String shippingAddress,
                                  String poNumber) {
        return buildOrder(null, guestEmail, null, false, cartItems, shippingRateId, shippingCost, shippingAddress, poNumber);
    }

    private Order buildOrder(UUID userId, String guestEmail, UUID companyId, boolean taxExempt,
                             List<CartItem> cartItems,
                             UUID shippingRateId, BigDecimal shippingCost, String shippingAddress,
                             String poNumber) {
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

        for (CartItem cartItem : cartItems) {
            inventoryService.reserveStock(cartItem.getVariantId(), cartItem.getQuantity());
        }

        BigDecimal itemsTotal = cartItems.stream()
            .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = shippingCost != null
            ? itemsTotal.add(shippingCost)
            : itemsTotal;

        Order order = new Order();
        order.setUserId(userId);
        order.setGuestEmail(guestEmail);
        order.setCompanyId(companyId);
        order.setTaxExempt(taxExempt);
        order.setTotalAmount(total);
        order.setShippingCost(shippingCost);
        order.setShippingRateId(shippingRateId);
        order.setShippingAddress(shippingAddress);
        order.setPoNumber(poNumber);
        order = orderRepo.save(order);

        for (CartItem cartItem : cartItems) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setVariantId(cartItem.getVariantId());
            item.setQuantity(cartItem.getQuantity());
            item.setUnitPrice(cartItem.getUnitPrice());
            orderItemRepo.save(item);
        }

        orderEventService.emit(order.getId(), OrderEventType.ORDER_PLACED,
            "Order placed", null, "system", null);

        return order;
    }

    @Transactional
    public Order createFromQuote(QuoteRequest quoteRequest, List<QuoteItem> quoteItems) {
        if (quoteItems.isEmpty()) {
            throw new ValidationException("EMPTY_QUOTE", "Quote has no items");
        }

        // Reserve inventory for items that are linked to a variant
        for (QuoteItem item : quoteItems) {
            if (item.getVariantId() != null) {
                inventoryService.reserveStock(item.getVariantId(), item.getQuantity());
            }
        }

        BigDecimal total = quoteItems.stream()
            .filter(i -> i.getUnitPrice() != null)
            .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setUserId(quoteRequest.getUserId());
        order.setTotalAmount(total);
        order = orderRepo.save(order);

        for (QuoteItem item : quoteItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setVariantId(item.getVariantId());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setUnitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
            orderItemRepo.save(orderItem);
        }

        orderEventService.emit(order.getId(), OrderEventType.ORDER_PLACED,
            "Order placed from quote", null, "system", null);

        return order;
    }

    @Transactional
    public void applyDiscount(UUID orderId, UUID discountId, BigDecimal discountAmount) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        order.setDiscountId(discountId);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(order.getTotalAmount().subtract(discountAmount));
        orderRepo.save(order);
    }

    @Transactional
    public void applyGiftCard(UUID orderId, UUID giftCardId, BigDecimal giftCardAmount) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        order.setGiftCardId(giftCardId);
        order.setGiftCardAmount(giftCardAmount);
        order.setTotalAmount(order.getTotalAmount().subtract(giftCardAmount));
        orderRepo.save(order);
    }

    @Transactional
    public void markPaidDirectly(UUID orderId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        order.setStatus(OrderStatus.PAID);
        orderRepo.save(order);
        orderItemRepo.findByOrderId(orderId).stream()
            .filter(item -> item.getVariantId() != null)
            .forEach(item -> inventoryService.confirmSale(item.getVariantId(), item.getQuantity()));
        orderEventService.emit(orderId, OrderEventType.PAYMENT_CONFIRMED,
            "Payment fulfilled via gift card", null, "system", null);
        sendOrderConfirmationEmail(order);
        if (order.getUserId() != null) autoTagService.applyOrderTags(order.getUserId());
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
        confirmPayment(stripeSessionId, stripePaymentIntentId, null);
    }

    @Transactional
    public void confirmPayment(String stripeSessionId, String stripePaymentIntentId, Long taxAmountCents) {
        orderRepo.findByStripeSessionId(stripeSessionId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PAID) return; // idempotent
            order.setStripePaymentIntentId(stripePaymentIntentId);
            order.setStatus(OrderStatus.PAID);
            if (taxAmountCents != null && taxAmountCents > 0) {
                order.setTaxAmount(BigDecimal.valueOf(taxAmountCents)
                    .divide(BigDecimal.valueOf(100)));
            }
            orderRepo.save(order);

            orderItemRepo.findByOrderId(order.getId()).stream()
                .filter(item -> item.getVariantId() != null)
                .forEach(item -> inventoryService.confirmSale(item.getVariantId(), item.getQuantity()));

            orderEventService.emit(order.getId(), OrderEventType.PAYMENT_CONFIRMED,
                "Payment confirmed via Stripe", null, "system", null);
            sendOrderConfirmationEmail(order);
            if (order.getUserId() != null) autoTagService.applyOrderTags(order.getUserId());
        });
    }

    @Transactional
    public void cancelBySession(String stripeSessionId) {
        orderRepo.findByStripeSessionId(stripeSessionId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.CANCELLED) return; // idempotent
            order.setStatus(OrderStatus.CANCELLED);
            orderRepo.save(order);

            orderItemRepo.findByOrderId(order.getId()).stream()
                .filter(item -> item.getVariantId() != null)
                .forEach(item -> inventoryService.releaseReservation(item.getVariantId(), item.getQuantity()));

            orderEventService.emit(order.getId(), OrderEventType.ORDER_CANCELLED,
                "Order cancelled (Stripe session expired)", null, "system", null);
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

        orderItemRepo.findByOrderId(orderId).stream()
            .filter(item -> item.getVariantId() != null)
            .forEach(item -> inventoryService.releaseReservation(item.getVariantId(), item.getQuantity()));

        orderEventService.emit(orderId, OrderEventType.ORDER_CANCELLED, "Order cancelled", null, "system", null);
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
        orderItemRepo.findByOrderId(orderId).stream()
            .filter(item -> item.getVariantId() != null)
            .forEach(item -> inventoryService.releaseReservation(item.getVariantId(), item.getQuantity()));
        String to = resolveCustomerEmail(order);
        if (to != null) emailService.sendOrderCancelled(to, shortRef(orderId), appProperties.getFrontendUrl());
        return toResponse(order);
    }

    @Transactional
    public void bulkCancel(List<UUID> ids) {
        List<Order> cancellable = orderRepo.findAllById(ids).stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT || o.getStatus() == OrderStatus.PAID)
            .toList();
        for (Order order : cancellable) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepo.save(order);
            orderItemRepo.findByOrderId(order.getId()).stream()
                .filter(item -> item.getVariantId() != null)
                .forEach(item -> inventoryService.releaseReservation(item.getVariantId(), item.getQuantity()));
            orderEventService.emit(order.getId(), OrderEventType.ORDER_CANCELLED, "Bulk cancelled", null, "admin", null);
            String to = resolveCustomerEmail(order);
            if (to != null) emailService.sendOrderCancelled(to, shortRef(order.getId()), appProperties.getFrontendUrl());
        }
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
        return PagedResult.of(orderRepo.findAll(buildSpec(filter), pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public String exportCsv(OrderFilter filter) {
        List<Order> orders = orderRepo.findAll(
            buildSpec(filter), Sort.by(Sort.Direction.DESC, "createdAt"));

        Set<UUID> orderIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
        Map<UUID, Long> itemCounts = orderIds.isEmpty() ? Map.of() :
            orderItemRepo.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.counting()));

        Set<UUID> userIds = orders.stream().map(Order::getUserId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> emailByUser = userIds.isEmpty() ? Map.of() :
            userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));

        StringBuilder sb = new StringBuilder();
        sb.append("id,created_at,status,customer_email,guest_email,items,total,currency," +
                  "discount_amount,gift_card_amount,shipping_cost,shipping_address\n");
        for (Order o : orders) {
            String customerEmail = o.getUserId() != null
                ? emailByUser.getOrDefault(o.getUserId(), "") : "";
            sb.append(csvCell(o.getId())).append(',')
              .append(csvCell(o.getCreatedAt())).append(',')
              .append(csvCell(o.getStatus())).append(',')
              .append(csvCell(customerEmail)).append(',')
              .append(csvCell(o.getGuestEmail())).append(',')
              .append(itemCounts.getOrDefault(o.getId(), 0L)).append(',')
              .append(csvCell(o.getTotalAmount())).append(',')
              .append(csvCell(o.getCurrency())).append(',')
              .append(csvCell(o.getDiscountAmount())).append(',')
              .append(csvCell(o.getGiftCardAmount())).append(',')
              .append(csvCell(o.getShippingCost())).append(',')
              .append(csvCell(o.getShippingAddress())).append('\n');
        }
        return sb.toString();
    }

    private void sendOrderConfirmationEmail(Order order) {
        String to = resolveCustomerEmail(order);
        if (to == null) return;
        List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
        Set<UUID> variantIds = items.stream().map(OrderItem::getVariantId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> titleByVariantId = variantIds.isEmpty() ? Map.of() :
            variantRepo.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getTitle));
        List<String> itemLines = items.stream().map(item -> {
            String title = item.getVariantId() != null
                ? titleByVariantId.getOrDefault(item.getVariantId(), "Item") : "Item";
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return String.format("%d × %s — $%.2f", item.getQuantity(), title, lineTotal);
        }).toList();
        emailService.sendOrderConfirmation(to, shortRef(order.getId()),
            order.getTotalAmount(), order.getCurrency(), itemLines, appProperties.getFrontendUrl());
    }

    private String resolveCustomerEmail(Order order) {
        if (order.getGuestEmail() != null) return order.getGuestEmail();
        if (order.getUserId() != null) {
            return userRepo.findById(order.getUserId()).map(User::getEmail).orElse(null);
        }
        return null;
    }

    private static String shortRef(UUID id) {
        return "#" + id.toString().substring(0, 8).toUpperCase();
    }

    private Specification<Order> buildSpec(OrderFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) predicates.add(cb.equal(root.get("status"), filter.status()));
            if (filter.userId() != null) predicates.add(cb.equal(root.get("userId"), filter.userId()));
            if (filter.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            if (filter.to() != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String csvCell(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @Transactional
    public OrderResponse adminRefundOrder(UUID orderId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (order.getStatus() == OrderStatus.REFUNDED) return toResponse(order);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException("INVALID_ORDER_STATUS",
                "Cannot refund order in status: " + order.getStatus());
        }
        if (order.getStripePaymentIntentId() == null) {
            throw new ConflictException("NO_PAYMENT_INTENT", "Order has no payment intent to refund");
        }
        try {
            com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                .setPaymentIntent(order.getStripePaymentIntentId())
                .build();
            stripeGateway.createRefund(params);
        } catch (com.stripe.exception.StripeException e) {
            throw new io.k2dv.garden.payment.exception.PaymentException(
                "STRIPE_REFUND_ERROR", "Failed to issue refund: " + e.getMessage());
        }
        order.setStatus(OrderStatus.REFUNDED);
        orderRepo.save(order);
        orderEventService.emit(orderId, OrderEventType.ADMIN_REFUND_ISSUED,
            "Admin issued full refund", null, "admin", null);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateOrder(UUID orderId, UpdateOrderRequest req) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (req.shippingAddress() != null) {
            if (order.getStatus() == OrderStatus.PARTIALLY_FULFILLED
                    || order.getStatus() == OrderStatus.FULFILLED) {
                throw new ConflictException("ORDER_ALREADY_SHIPPED",
                    "Cannot update shipping address after order has been shipped");
            }
            order.setShippingAddress(req.shippingAddress());
        }
        if (req.adminNotes() != null) {
            order.setAdminNotes(req.adminNotes());
            orderEventService.emit(orderId, OrderEventType.NOTE_ADDED,
                "Admin note added", null, "admin", null);
        }
        if (req.poNumber() != null) {
            order.setPoNumber(req.poNumber());
        }
        return toResponse(orderRepo.save(order));
    }

    @Transactional
    public OrderResponse refundOrder(UUID orderId, UUID requestingUserId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (!order.getUserId().equals(requestingUserId)) {
            throw new ValidationException("ORDER_NOT_OWNED", "Order does not belong to current user");
        }
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return toResponse(order);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException("INVALID_ORDER_STATUS",
                "Cannot refund order in status: " + order.getStatus());
        }
        if (order.getStripePaymentIntentId() == null) {
            throw new ConflictException("NO_PAYMENT_INTENT", "Order has no payment intent to refund");
        }
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(order.getStripePaymentIntentId())
                .build();
            stripeGateway.createRefund(params);
        } catch (StripeException e) {
            throw new PaymentException("STRIPE_REFUND_ERROR", "Failed to issue refund: " + e.getMessage());
        }
        order.setStatus(OrderStatus.REFUNDED);
        orderRepo.save(order);
        orderEventService.emit(orderId, OrderEventType.ORDER_REFUNDED,
            "Customer-initiated refund", null, "system", null);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse createDraft(CreateDraftOrderRequest req) {
        if (req.userId() == null && (req.guestEmail() == null || req.guestEmail().isBlank())) {
            throw new ValidationException("USER_OR_EMAIL_REQUIRED", "Either userId or guestEmail is required");
        }

        BigDecimal total = req.items().stream()
            .map(i -> {
                ProductVariant v = variantRepo.findByIdAndDeletedAtIsNull(i.variantId())
                    .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found: " + i.variantId()));
                BigDecimal price = i.unitPrice() != null ? i.unitPrice() : v.getPrice();
                return price.multiply(BigDecimal.valueOf(i.quantity()));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setUserId(req.userId());
        order.setGuestEmail(req.guestEmail());
        order.setStatus(OrderStatus.DRAFT);
        order.setTotalAmount(total);
        order.setCurrency(req.currency() != null ? req.currency() : "usd");
        order.setShippingAddress(req.shippingAddress());
        order.setPoNumber(req.poNumber());
        order.setCompanyId(req.companyId());
        order = orderRepo.save(order);

        for (var item : req.items()) {
            ProductVariant v = variantRepo.findByIdAndDeletedAtIsNull(item.variantId()).orElseThrow();
            BigDecimal price = item.unitPrice() != null ? item.unitPrice() : v.getPrice();
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId());
            oi.setVariantId(item.variantId());
            oi.setQuantity(item.quantity());
            oi.setUnitPrice(price);
            orderItemRepo.save(oi);
        }
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateDraftItems(UUID orderId, java.util.List<io.k2dv.garden.order.dto.DraftOrderItemRequest> items) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new ConflictException("NOT_DRAFT", "Order is not in DRAFT status");
        }
        orderItemRepo.deleteAll(orderItemRepo.findByOrderId(orderId));

        BigDecimal total = BigDecimal.ZERO;
        for (var item : items) {
            ProductVariant v = variantRepo.findByIdAndDeletedAtIsNull(item.variantId())
                .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found: " + item.variantId()));
            BigDecimal price = item.unitPrice() != null ? item.unitPrice() : v.getPrice();
            OrderItem oi = new OrderItem();
            oi.setOrderId(orderId);
            oi.setVariantId(item.variantId());
            oi.setQuantity(item.quantity());
            oi.setUnitPrice(price);
            orderItemRepo.save(oi);
            total = total.add(price.multiply(BigDecimal.valueOf(item.quantity())));
        }
        order.setTotalAmount(total);
        return toResponse(orderRepo.save(order));
    }

    @Transactional
    public OrderResponse completeDraft(UUID orderId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new ConflictException("NOT_DRAFT", "Order is not in DRAFT status");
        }
        List<OrderItem> items = orderItemRepo.findByOrderId(orderId);
        if (items.isEmpty()) {
            throw new ValidationException("EMPTY_ORDER", "Draft order has no items");
        }
        for (OrderItem item : items) {
            inventoryService.reserveStock(item.getVariantId(), item.getQuantity());
        }
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        return toResponse(orderRepo.save(order));
    }

    @Transactional
    public OrderResponse updateMetadata(UUID orderId, java.util.Map<String, Object> metadata) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        order.setMetadata(metadata);
        return toResponse(orderRepo.save(order));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItem> orderItems = orderItemRepo.findByOrderId(order.getId());

        Set<UUID> variantIds = orderItems.stream()
            .map(OrderItem::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantsById = variantRepo.findAllById(variantIds).stream()
            .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        Set<UUID> productIds = variantsById.values().stream()
            .map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> productsById = productRepo.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        Map<UUID, String> resolvedImageUrls = imageResolver.resolveByProductId(productsById.values());

        List<OrderItemResponse> items = orderItems.stream().map(i -> {
            OrderItemProductInfo productInfo = null;
            if (i.getVariantId() != null) {
                ProductVariant variant = variantsById.get(i.getVariantId());
                if (variant != null) {
                    Product product = productsById.get(variant.getProductId());
                    if (product != null) {
                        productInfo = new OrderItemProductInfo(
                            product.getId(),
                            product.getTitle(),
                            variant.getTitle(),
                            resolvedImageUrls.get(product.getId()));
                    }
                }
            }
            return new OrderItemResponse(i.getId(), i.getVariantId(), i.getQuantity(), i.getUnitPrice(), productInfo);
        }).toList();

        return new OrderResponse(order.getId(), order.getUserId(), order.getGuestEmail(),
            order.getStatus(), order.getTotalAmount(), order.getCurrency(),
            order.getStripeSessionId(), order.getDiscountId(), order.getDiscountAmount(),
            order.getGiftCardId(), order.getGiftCardAmount(), order.getAdminNotes(),
            order.getShippingAddress(), order.getShippingCost(), order.getShippingRateId(),
            items, order.getTaxAmount(), order.getPoNumber(), order.getMetadata(), order.getCreatedAt());
    }
}
