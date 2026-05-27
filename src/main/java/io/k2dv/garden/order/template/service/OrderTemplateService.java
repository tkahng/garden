package io.k2dv.garden.order.template.service;

import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.cart.model.CartStatus;
import io.k2dv.garden.cart.repository.CartItemRepository;
import io.k2dv.garden.cart.repository.CartRepository;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.order.template.dto.*;
import io.k2dv.garden.order.template.model.OrderTemplate;
import io.k2dv.garden.order.template.model.OrderTemplateItem;
import io.k2dv.garden.order.template.repository.OrderTemplateItemRepository;
import io.k2dv.garden.order.template.repository.OrderTemplateRepository;
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

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Allows authenticated users to save named lists of product variants as reusable order templates.
 * Loading a template replaces the user's active cart contents with the template items,
 * silently skipping any variants that are no longer available or whose products are inactive.
 */
@Service
@RequiredArgsConstructor
public class OrderTemplateService {

    private final OrderTemplateRepository templateRepo;
    private final OrderTemplateItemRepository templateItemRepo;
    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;
    private final CartService cartService;

    /**
     * Creates a new named order template for the user with the specified variant and quantity list.
     */
    @Transactional
    public OrderTemplateResponse create(UUID userId, CreateOrderTemplateRequest req) {
        OrderTemplate template = new OrderTemplate();
        template.setUserId(userId);
        template.setName(req.name().strip());
        template = templateRepo.save(template);

        List<OrderTemplateItem> items = new ArrayList<>();
        for (var input : req.items()) {
            OrderTemplateItem item = new OrderTemplateItem();
            item.setTemplateId(template.getId());
            item.setVariantId(input.variantId());
            item.setQuantity(input.quantity());
            items.add(templateItemRepo.save(item));
        }

        return toResponse(template, items);
    }

    /**
     * Returns all templates owned by the user, newest first, with variant titles batch-fetched
     * to avoid N+1 queries on the item list.
     */
    @Transactional(readOnly = true)
    public List<OrderTemplateResponse> listForUser(UUID userId) {
        List<OrderTemplate> templates = templateRepo.findByUserIdOrderByCreatedAtDesc(userId);
        if (templates.isEmpty()) return List.of();

        List<UUID> ids = templates.stream().map(OrderTemplate::getId).toList();

        List<OrderTemplateItem> allItems = new ArrayList<>();
        for (UUID id : ids) {
            allItems.addAll(templateItemRepo.findByTemplateId(id));
        }
        Map<UUID, List<OrderTemplateItem>> byTemplate = allItems.stream()
            .collect(Collectors.groupingBy(OrderTemplateItem::getTemplateId));

        Set<UUID> variantIds = allItems.stream().map(OrderTemplateItem::getVariantId).collect(Collectors.toSet());
        Map<UUID, String> titleById = variantIds.isEmpty() ? Map.of() :
            variantRepo.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getTitle));

        return templates.stream()
            .map(t -> toResponse(t, byTemplate.getOrDefault(t.getId(), List.of()), titleById))
            .toList();
    }

    /**
     * Retrieves a single template, enforcing that it belongs to the requesting user.
     */
    @Transactional(readOnly = true)
    public OrderTemplateResponse getById(UUID userId, UUID templateId) {
        OrderTemplate template = requireOwned(userId, templateId);
        List<OrderTemplateItem> items = templateItemRepo.findByTemplateId(templateId);
        return toResponse(template, items);
    }

    /**
     * Permanently deletes a template and all its line items; enforces ownership before deletion.
     */
    @Transactional
    public void delete(UUID userId, UUID templateId) {
        requireOwned(userId, templateId);
        templateItemRepo.deleteByTemplateId(templateId);
        templateRepo.deleteById(templateId);
    }

    /**
     * Replaces the user's active cart contents with the items from the named template, using current
     * retail prices rather than any historical price stored in the template. Variants that have
     * been deleted or whose products are no longer active are silently skipped.
     */
    @Transactional
    public CartResponse loadToCart(UUID userId, UUID templateId) {
        OrderTemplate template = requireOwned(userId, templateId);
        List<OrderTemplateItem> items = templateItemRepo.findByTemplateId(template.getId());
        if (items.isEmpty()) {
            throw new ValidationException("EMPTY_TEMPLATE", "Template has no items");
        }

        Cart cart = cartRepo.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .orElseGet(() -> {
                Cart c = new Cart();
                c.setUserId(userId);
                return cartRepo.save(c);
            });

        cartItemRepo.deleteAll(cartItemRepo.findByCartId(cart.getId()));

        for (OrderTemplateItem ti : items) {
            ProductVariant variant = variantRepo.findByIdAndDeletedAtIsNull(ti.getVariantId()).orElse(null);
            if (variant == null) continue;
            Product product = productRepo.findByIdAndDeletedAtIsNull(variant.getProductId()).orElse(null);
            if (product == null || product.getStatus() != ProductStatus.ACTIVE) continue;

            BigDecimal price = variant.getPrice();
            CartItem cartItem = new CartItem();
            cartItem.setCartId(cart.getId());
            cartItem.setVariantId(ti.getVariantId());
            cartItem.setQuantity(ti.getQuantity());
            cartItem.setUnitPrice(price);
            cartItemRepo.save(cartItem);
        }

        return cartService.getOrCreateActiveCart(userId);
    }

    private OrderTemplate requireOwned(UUID userId, UUID templateId) {
        return templateRepo.findByIdAndUserId(templateId, userId)
            .orElseThrow(() -> new NotFoundException("TEMPLATE_NOT_FOUND", "Order template not found"));
    }

    private OrderTemplateResponse toResponse(OrderTemplate t, List<OrderTemplateItem> items) {
        Set<UUID> variantIds = items.stream().map(OrderTemplateItem::getVariantId).collect(Collectors.toSet());
        Map<UUID, String> titleById = variantIds.isEmpty() ? Map.of() :
            variantRepo.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getTitle));
        return toResponse(t, items, titleById);
    }

    private OrderTemplateResponse toResponse(OrderTemplate t, List<OrderTemplateItem> items,
                                              Map<UUID, String> titleById) {
        List<OrderTemplateItemResponse> itemResponses = items.stream()
            .map(i -> OrderTemplateItemResponse.from(i, titleById.get(i.getVariantId())))
            .toList();
        return OrderTemplateResponse.from(t, itemResponses);
    }
}
