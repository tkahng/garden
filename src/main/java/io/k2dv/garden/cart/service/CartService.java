package io.k2dv.garden.cart.service;

import io.k2dv.garden.b2b.repository.CompanyMembershipRepository;
import io.k2dv.garden.b2b.service.PriceListService;
import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.dto.BulkAddToCartResponse;
import io.k2dv.garden.cart.dto.CartItemProductInfo;
import io.k2dv.garden.cart.dto.CartItemResponse;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.dto.UpdateCartItemRequest;
import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.cart.model.CartStatus;
import io.k2dv.garden.cart.repository.CartItemRepository;
import io.k2dv.garden.cart.repository.CartRepository;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderItem;
import io.k2dv.garden.order.repository.OrderItemRepository;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.product.service.ProductImageResolver;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;
    private final ProductImageResolver imageResolver;
    private final PriceListService priceListService;
    private final CompanyMembershipRepository membershipRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;

    @Transactional
    public CartResponse getOrCreateActiveCart(UUID userId) {
        Cart cart = cartRepo.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .orElseGet(() -> {
                Cart c = new Cart();
                c.setUserId(userId);
                return cartRepo.save(c);
            });
        return toResponse(cart);
    }

    @Transactional
    public CartResponse setCompanyContext(UUID userId, UUID companyId) {
        if (!membershipRepo.existsByCompanyIdAndUserId(companyId, userId)) {
            throw new ValidationException("NOT_A_MEMBER", "User is not a member of this company");
        }
        Cart cart = cartRepo.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .orElseGet(() -> {
                Cart c = new Cart();
                c.setUserId(userId);
                return cartRepo.save(c);
            });
        cart.setCompanyId(companyId);
        cartRepo.save(cart);

        // Re-price existing items using the new company context
        List<CartItem> items = cartItemRepo.findByCartId(cart.getId());
        for (CartItem item : items) {
            BigDecimal price = resolvePrice(companyId, item.getVariantId(), item.getQuantity());
            item.setUnitPrice(price);
            cartItemRepo.save(item);
        }

        return toResponse(cart);
    }

    @Transactional
    public CartResponse clearCompanyContext(UUID userId) {
        Cart cart = findActiveCartOrThrow(userId);
        cart.setCompanyId(null);
        cartRepo.save(cart);

        // Re-price items back to base variant price
        List<CartItem> items = cartItemRepo.findByCartId(cart.getId());
        for (CartItem item : items) {
            variantRepo.findByIdAndDeletedAtIsNull(item.getVariantId()).ifPresentOrElse(
                variant -> {
                    if (variant.getPrice() != null) {
                        item.setUnitPrice(variant.getPrice());
                        cartItemRepo.save(item);
                    }
                },
                () -> log.warn("Variant {} not found while re-pricing cart item {}; item retained at existing price",
                    item.getVariantId(), item.getId())
            );
        }

        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(UUID userId, AddCartItemRequest req) {
        Cart cart = findActiveCartOrThrow(userId);
        ProductVariant variant = variantRepo.findByIdAndDeletedAtIsNull(req.variantId())
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        Product product = productRepo.findByIdAndDeletedAtIsNull(variant.getProductId())
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ValidationException("PRODUCT_NOT_ACTIVE", "Product is not available for purchase");
        }
        if (variant.getPrice() == null && cart.getCompanyId() == null) {
            throw new ValidationException("QUOTE_ONLY_VARIANT",
                "This variant is quote-only and cannot be added to the regular cart");
        }

        CartItem item = cartItemRepo.findByCartIdAndVariantId(cart.getId(), req.variantId())
            .orElseGet(() -> {
                CartItem i = new CartItem();
                i.setCartId(cart.getId());
                i.setVariantId(req.variantId());
                return i;
            });
        int newQty = item.getQuantity() + req.quantity();
        int moq = variant.getMinimumOrderQty();
        if (newQty < moq) {
            throw new ValidationException("BELOW_MIN_ORDER_QTY",
                "Minimum order quantity for this item is " + moq);
        }
        item.setQuantity(newQty);
        item.setUnitPrice(resolveItemPrice(cart, variant, newQty));
        cartItemRepo.save(item);

        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(UUID userId, UUID itemId, UpdateCartItemRequest req) {
        Cart cart = findActiveCartOrThrow(userId);
        CartItem item = cartItemRepo.findByIdAndCartId(itemId, cart.getId())
            .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "Cart item not found"));
        ProductVariant variant = variantRepo.findByIdAndDeletedAtIsNull(item.getVariantId())
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        int moq = variant.getMinimumOrderQty();
        if (req.quantity() < moq) {
            throw new ValidationException("BELOW_MIN_ORDER_QTY",
                "Minimum order quantity for this item is " + moq);
        }
        item.setQuantity(req.quantity());
        // Re-price on qty change — volume tiers may apply
        if (cart.getCompanyId() != null) {
            item.setUnitPrice(resolvePrice(cart.getCompanyId(), item.getVariantId(), req.quantity()));
        }
        cartItemRepo.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(UUID userId, UUID itemId) {
        Cart cart = findActiveCartOrThrow(userId);
        CartItem item = cartItemRepo.findByIdAndCartId(itemId, cart.getId())
            .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "Cart item not found"));
        cartItemRepo.delete(item);
        return toResponse(cart);
    }

    @Transactional
    public void abandonCart(UUID userId) {
        cartRepo.findByUserIdAndStatus(userId, CartStatus.ACTIVE).ifPresent(cart -> {
            cart.setStatus(CartStatus.ABANDONED);
            cartRepo.save(cart);
        });
    }

    // --- Guest cart ---

    @Transactional
    public CartResponse getOrCreateGuestCart(UUID sessionId) {
        Cart cart = cartRepo.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE)
            .orElseGet(() -> {
                Cart c = new Cart();
                c.setSessionId(sessionId);
                return cartRepo.save(c);
            });
        return toResponse(cart);
    }

    @Transactional
    public CartResponse addGuestItem(UUID sessionId, AddCartItemRequest req) {
        Cart cart = findActiveGuestCartOrThrow(sessionId);
        ProductVariant variant = variantRepo.findByIdAndDeletedAtIsNull(req.variantId())
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        Product product = productRepo.findByIdAndDeletedAtIsNull(variant.getProductId())
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ValidationException("PRODUCT_NOT_ACTIVE", "Product is not available for purchase");
        }
        if (variant.getPrice() == null) {
            throw new ValidationException("QUOTE_ONLY_VARIANT",
                "This variant is quote-only and cannot be added to the regular cart");
        }
        CartItem item = cartItemRepo.findByCartIdAndVariantId(cart.getId(), req.variantId())
            .orElseGet(() -> {
                CartItem i = new CartItem();
                i.setCartId(cart.getId());
                i.setVariantId(req.variantId());
                i.setUnitPrice(variant.getPrice());
                return i;
            });
        item.setQuantity(item.getQuantity() + req.quantity());
        cartItemRepo.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateGuestItem(UUID sessionId, UUID itemId, UpdateCartItemRequest req) {
        Cart cart = findActiveGuestCartOrThrow(sessionId);
        CartItem item = cartItemRepo.findByIdAndCartId(itemId, cart.getId())
            .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "Cart item not found"));
        item.setQuantity(req.quantity());
        cartItemRepo.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeGuestItem(UUID sessionId, UUID itemId) {
        Cart cart = findActiveGuestCartOrThrow(sessionId);
        CartItem item = cartItemRepo.findByIdAndCartId(itemId, cart.getId())
            .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "Cart item not found"));
        cartItemRepo.delete(item);
        return toResponse(cart);
    }

    @Transactional
    public void abandonGuestCart(UUID sessionId) {
        cartRepo.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE).ifPresent(cart -> {
            cart.setStatus(CartStatus.ABANDONED);
            cartRepo.save(cart);
        });
    }

    @Transactional
    public CartResponse reorderFromHistory(UUID userId, UUID orderId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new ValidationException("ORDER_NOT_OWNED", "Order does not belong to current user");
        }
        List<OrderItem> orderItems = orderItemRepo.findByOrderId(orderId);
        if (orderItems.isEmpty()) {
            throw new ValidationException("EMPTY_ORDER", "Order has no items to reorder");
        }

        Cart cart = cartRepo.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .orElseGet(() -> {
                Cart c = new Cart();
                c.setUserId(userId);
                return cartRepo.save(c);
            });
        cartItemRepo.deleteAll(cartItemRepo.findByCartId(cart.getId()));

        // Batch-fetch variants and products to avoid per-item queries
        Set<UUID> variantIds = orderItems.stream()
            .map(OrderItem::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantsById = variantRepo.findAllById(variantIds).stream()
            .filter(v -> v.getDeletedAt() == null)
            .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        Set<UUID> productIds = variantsById.values().stream()
            .map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> productsById = productRepo.findAllById(productIds).stream()
            .filter(p -> p.getDeletedAt() == null && p.getStatus() == ProductStatus.ACTIVE)
            .collect(Collectors.toMap(Product::getId, p -> p));

        List<CartItem> newItems = new java.util.ArrayList<>();
        for (OrderItem oi : orderItems) {
            if (oi.getVariantId() == null) continue;
            ProductVariant variant = variantsById.get(oi.getVariantId());
            if (variant == null) continue;
            if (!productsById.containsKey(variant.getProductId())) continue;

            BigDecimal price = resolveItemPrice(cart, variant, oi.getQuantity());
            CartItem item = new CartItem();
            item.setCartId(cart.getId());
            item.setVariantId(oi.getVariantId());
            item.setQuantity(oi.getQuantity());
            item.setUnitPrice(price);
            newItems.add(item);
        }
        cartItemRepo.saveAll(newItems);
        return toResponse(cart);
    }

    // --- Internal API for PaymentService ---

    @Transactional(readOnly = true)
    public Cart requireActiveCart(UUID userId) {
        return findActiveCartOrThrow(userId);
    }

    @Transactional(readOnly = true)
    public Cart requireActiveGuestCart(UUID sessionId) {
        return findActiveGuestCartOrThrow(sessionId);
    }

    private Cart findActiveCartOrThrow(UUID userId) {
        return cartRepo.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .orElseThrow(() -> new ValidationException("NO_ACTIVE_CART", "No active cart found"));
    }

    private Cart findActiveGuestCartOrThrow(UUID sessionId) {
        return cartRepo.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE)
            .orElseThrow(() -> new ValidationException("NO_ACTIVE_CART", "No active guest cart found"));
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(UUID cartId) {
        return cartItemRepo.findByCartId(cartId);
    }

    @Transactional
    public void markCheckedOut(UUID cartId) {
        cartRepo.findById(cartId).ifPresent(cart -> {
            cart.setStatus(CartStatus.CHECKED_OUT);
            cartRepo.save(cart);
        });
    }

    // --- Price resolution ---

    private BigDecimal resolveItemPrice(Cart cart, ProductVariant variant, int qty) {
        if (cart.getCompanyId() != null) {
            return resolvePrice(cart.getCompanyId(), variant.getId(), qty);
        }
        return variant.getPrice();
    }

    private BigDecimal resolvePrice(UUID companyId, UUID variantId, int qty) {
        return priceListService.resolvePrice(companyId, variantId, qty).price();
    }

    // --- Helpers ---

    private CartResponse toResponse(Cart cart) {
        List<CartItem> cartItems = cartItemRepo.findByCartId(cart.getId());

        Set<UUID> variantIds = cartItems.stream().map(CartItem::getVariantId).collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantsById = variantRepo.findAllById(variantIds).stream()
            .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        Set<UUID> productIds = variantsById.values().stream()
            .map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> productsById = productRepo.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        Map<UUID, String> resolvedImageUrls = imageResolver.resolveByProductId(productsById.values());

        List<CartItemResponse> items = cartItems.stream().map(i -> {
            ProductVariant variant = variantsById.get(i.getVariantId());
            CartItemProductInfo productInfo = null;
            if (variant != null) {
                Product product = productsById.get(variant.getProductId());
                if (product != null) {
                    productInfo = new CartItemProductInfo(
                        product.getId(),
                        product.getTitle(),
                        variant.getTitle(),
                        resolvedImageUrls.get(product.getId()));
                }
            }
            int moq = variant != null ? variant.getMinimumOrderQty() : 1;
            return new CartItemResponse(i.getId(), i.getVariantId(), i.getQuantity(), i.getUnitPrice(), productInfo, moq);
        }).toList();

        return new CartResponse(cart.getId(), cart.getStatus(), cart.getCompanyId(), items, cart.getCreatedAt());
    }

    private static final int CSV_MAX_ROWS = 500;

    @Transactional
    public BulkAddToCartResponse addItemsFromCsv(UUID userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ValidationException("CSV_EMPTY", "Uploaded file is empty");
        }
        // Ensure a cart exists before processing rows
        getOrCreateActiveCart(userId);
        List<BulkAddToCartResponse.LineResult> results = new ArrayList<>();
        int rowCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isBlank()) continue;
                if (first) { first = false; if (line.toLowerCase().startsWith("sku")) continue; }
                if (++rowCount > CSV_MAX_ROWS) {
                    throw new ValidationException("CSV_TOO_LARGE",
                        "CSV exceeds the maximum of " + CSV_MAX_ROWS + " data rows");
                }
                String[] parts = line.split(",", -1);
                if (parts.length < 2) continue;
                String sku = parts[0].trim();
                int qty;
                try { qty = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException e) { continue; }
                if (sku.isBlank() || qty < 1) continue;

                var variant = variantRepo.findBySkuIgnoreCaseAndDeletedAtIsNull(sku);
                if (variant.isEmpty()) {
                    results.add(new BulkAddToCartResponse.LineResult(sku, qty,
                        BulkAddToCartResponse.Status.NOT_FOUND, null, null, "SKU not found"));
                    continue;
                }
                try {
                    addItem(userId, new AddCartItemRequest(variant.get().getId(), qty));
                    UUID productId = variant.get().getProductId();
                    String productTitle = productRepo.findByIdAndDeletedAtIsNull(productId)
                        .map(p -> p.getTitle())
                        .orElseGet(() -> {
                            log.warn("Product {} not found for variant {} during CSV import; title omitted",
                                productId, variant.get().getId());
                            return null;
                        });
                    results.add(new BulkAddToCartResponse.LineResult(sku, qty,
                        BulkAddToCartResponse.Status.ADDED, variant.get().getId(),
                        productTitle, null));
                } catch (Exception e) {
                    results.add(new BulkAddToCartResponse.LineResult(sku, qty,
                        BulkAddToCartResponse.Status.ERROR, variant.get().getId(), null, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new ValidationException("CSV_READ_ERROR", "Failed to read CSV file: " + e.getMessage());
        }
        // Fetch the cart again to include all items added during CSV processing
        return new BulkAddToCartResponse(getOrCreateActiveCart(userId), results);
    }
}
