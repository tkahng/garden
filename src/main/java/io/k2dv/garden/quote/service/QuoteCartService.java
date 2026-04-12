package io.k2dv.garden.quote.service;

import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.quote.dto.*;
import io.k2dv.garden.quote.model.QuoteCart;
import io.k2dv.garden.quote.model.QuoteCartItem;
import io.k2dv.garden.quote.model.QuoteCartStatus;
import io.k2dv.garden.quote.repository.QuoteCartItemRepository;
import io.k2dv.garden.quote.repository.QuoteCartRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteCartService {

    private final QuoteCartRepository cartRepo;
    private final QuoteCartItemRepository itemRepo;
    private final ProductVariantRepository variantRepo;

    @Transactional
    public QuoteCartResponse getOrCreateActiveCart(UUID userId) {
        QuoteCart cart = cartRepo.findByUserIdAndStatus(userId, QuoteCartStatus.ACTIVE)
            .orElseGet(() -> {
                QuoteCart c = new QuoteCart();
                c.setUserId(userId);
                return cartRepo.save(c);
            });
        return toResponse(cart);
    }

    @Transactional
    public void clearCart(UUID userId) {
        cartRepo.findByUserIdAndStatus(userId, QuoteCartStatus.ACTIVE).ifPresent(cart -> {
            itemRepo.findByQuoteCartId(cart.getId()).forEach(itemRepo::delete);
        });
    }

    @Transactional
    public QuoteCartResponse addItem(UUID userId, AddQuoteCartItemRequest req) {
        QuoteCart cart = findActiveCartOrThrow(userId);

        ProductVariant variant = variantRepo.findByIdAndDeletedAtIsNull(req.variantId())
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));

        // Only quote-only variants (null price) or any variant can be added to quote cart
        // The spec doesn't restrict what goes in the quote cart, just that null-price variants
        // cannot go in the REGULAR cart. Quote cart accepts any active variant.

        QuoteCartItem item = itemRepo.findByQuoteCartIdAndVariantId(cart.getId(), req.variantId())
            .orElseGet(() -> {
                QuoteCartItem i = new QuoteCartItem();
                i.setQuoteCartId(cart.getId());
                i.setVariantId(req.variantId());
                return i;
            });
        item.setQuantity(item.getQuantity() + req.quantity());
        item.setNote(req.note());
        itemRepo.save(item);

        return toResponse(cart);
    }

    @Transactional
    public QuoteCartResponse updateItem(UUID userId, UUID itemId, UpdateQuoteCartItemRequest req) {
        QuoteCart cart = findActiveCartOrThrow(userId);
        QuoteCartItem item = itemRepo.findByIdAndQuoteCartId(itemId, cart.getId())
            .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "Quote cart item not found"));
        item.setQuantity(req.quantity());
        item.setNote(req.note());
        itemRepo.save(item);
        return toResponse(cart);
    }

    @Transactional
    public QuoteCartResponse removeItem(UUID userId, UUID itemId) {
        QuoteCart cart = findActiveCartOrThrow(userId);
        QuoteCartItem item = itemRepo.findByIdAndQuoteCartId(itemId, cart.getId())
            .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "Quote cart item not found"));
        itemRepo.delete(item);
        return toResponse(cart);
    }

    // --- Internal API ---

    @Transactional(readOnly = true)
    public QuoteCart requireActiveCart(UUID userId) {
        return findActiveCartOrThrow(userId);
    }

    @Transactional(readOnly = true)
    public List<QuoteCartItem> getCartItems(UUID cartId) {
        return itemRepo.findByQuoteCartId(cartId);
    }

    @Transactional
    public void markSubmitted(UUID cartId) {
        cartRepo.findById(cartId).ifPresent(cart -> {
            cart.setStatus(QuoteCartStatus.SUBMITTED);
            cartRepo.save(cart);
        });
    }

    // --- Helpers ---

    private QuoteCart findActiveCartOrThrow(UUID userId) {
        return cartRepo.findByUserIdAndStatus(userId, QuoteCartStatus.ACTIVE)
            .orElseThrow(() -> new ValidationException("NO_ACTIVE_QUOTE_CART", "No active quote cart found"));
    }

    private QuoteCartResponse toResponse(QuoteCart cart) {
        List<QuoteCartItem> items = itemRepo.findByQuoteCartId(cart.getId());
        List<QuoteCartItemResponse> itemResponses = items.stream()
            .map(i -> new QuoteCartItemResponse(i.getId(), i.getVariantId(), i.getQuantity(), i.getNote(), i.getCreatedAt()))
            .toList();
        return new QuoteCartResponse(cart.getId(), cart.getStatus(), itemResponses, cart.getCreatedAt());
    }
}
