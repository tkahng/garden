package io.k2dv.garden.cart.service;

import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.dto.CartItemProductInfo;
import io.k2dv.garden.cart.dto.CartItemResponse;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.dto.UpdateCartItemRequest;
import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.cart.model.CartStatus;
import io.k2dv.garden.cart.repository.CartItemRepository;
import io.k2dv.garden.cart.repository.CartRepository;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.product.service.ProductImageResolver;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;
    private final ProductImageResolver imageResolver;

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
    public CartResponse addItem(UUID userId, AddCartItemRequest req) {
        Cart cart = findActiveCartOrThrow(userId);
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
    public CartResponse updateItem(UUID userId, UUID itemId, UpdateCartItemRequest req) {
        Cart cart = findActiveCartOrThrow(userId);
        CartItem item = cartItemRepo.findByIdAndCartId(itemId, cart.getId())
            .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "Cart item not found"));
        item.setQuantity(req.quantity());
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

    // --- Helpers ---

    private CartResponse toResponse(Cart cart) {
        List<CartItem> cartItems = cartItemRepo.findByCartId(cart.getId());

        // Batch-load variants
        Set<UUID> variantIds = cartItems.stream().map(CartItem::getVariantId).collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantsById = variantRepo.findAllById(variantIds).stream()
            .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        // Batch-load products
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
            return new CartItemResponse(i.getId(), i.getVariantId(), i.getQuantity(), i.getUnitPrice(), productInfo);
        }).toList();

        return new CartResponse(cart.getId(), cart.getStatus(), items, cart.getCreatedAt());
    }
}
