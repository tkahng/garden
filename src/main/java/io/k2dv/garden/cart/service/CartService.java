package io.k2dv.garden.cart.service;

import io.k2dv.garden.cart.dto.AddCartItemRequest;
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
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;

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

    // --- Internal API for PaymentService ---

    @Transactional(readOnly = true)
    public Cart requireActiveCart(UUID userId) {
        return findActiveCartOrThrow(userId);
    }

    private Cart findActiveCartOrThrow(UUID userId) {
        return cartRepo.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .orElseThrow(() -> new ValidationException("NO_ACTIVE_CART", "No active cart found"));
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
        List<CartItemResponse> items = cartItemRepo.findByCartId(cart.getId()).stream()
            .map(i -> new CartItemResponse(i.getId(), i.getVariantId(), i.getQuantity(), i.getUnitPrice()))
            .toList();
        return new CartResponse(cart.getId(), cart.getStatus(), items, cart.getCreatedAt());
    }
}
