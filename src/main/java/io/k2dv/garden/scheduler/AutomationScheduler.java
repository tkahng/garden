package io.k2dv.garden.scheduler;

import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.cart.repository.CartItemRepository;
import io.k2dv.garden.cart.repository.CartRepository;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.inventory.model.InventoryItem;
import io.k2dv.garden.inventory.model.InventoryLevel;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationScheduler {

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final UserRepository userRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;
    private final InventoryLevelRepository inventoryLevelRepo;
    private final InventoryItemRepository inventoryItemRepo;
    private final EmailService emailService;
    private final AppProperties appProperties;

    // ── Abandoned cart recovery ───────────────────────────────────────────────

    @Scheduled(cron = "0 0 * * * *") // top of every hour
    @Transactional
    public void sendAbandonedCartReminders() {
        Instant cutoff = Instant.now().minus(appProperties.getAutomation().getAbandonedCartDelay());
        List<Cart> carts = cartRepo.findAbandonedCarts(cutoff);
        if (carts.isEmpty()) return;

        log.info("Found {} abandoned cart(s) to remind", carts.size());

        Set<UUID> userIds = carts.stream().map(Cart::getUserId).collect(Collectors.toSet());
        Map<UUID, User> usersById = userRepo.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        for (Cart cart : carts) {
            User user = usersById.get(cart.getUserId());
            if (user == null) continue;

            List<String> itemLines = buildCartItemLines(cart.getId());
            if (itemLines.isEmpty()) continue;

            String cartUrl = appProperties.getFrontendUrl() + "/cart";
            emailService.sendAbandonedCartReminder(user.getEmail(), user.getFirstName(), itemLines, cartUrl);

            cart.setAbandonedReminderSentAt(Instant.now());
            cartRepo.save(cart);
        }

        log.info("Sent abandoned-cart reminders for {} cart(s)", carts.size());
    }

    private List<String> buildCartItemLines(UUID cartId) {
        List<CartItem> items = cartItemRepo.findByCartId(cartId);
        if (items.isEmpty()) return List.of();

        Set<UUID> variantIds = items.stream().map(CartItem::getVariantId).collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantsById = variantRepo.findAllById(variantIds).stream()
            .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        Set<UUID> productIds = variantsById.values().stream()
            .map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<UUID, String> titleByProductId = productRepo.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Product::getTitle));

        return items.stream().map(item -> {
            ProductVariant variant = variantsById.get(item.getVariantId());
            String productTitle = variant != null
                ? titleByProductId.getOrDefault(variant.getProductId(), "Item") : "Item";
            String variantTitle = variant != null ? variant.getTitle() : "";
            String label = variantTitle.isBlank() || variantTitle.equals(productTitle)
                ? productTitle : productTitle + " — " + variantTitle;
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return String.format("%d × %s — $%.2f", item.getQuantity(), label, lineTotal);
        }).toList();
    }

    // ── Low-stock alerts ──────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 */4 * * *") // every 4 hours
    @Transactional
    public void checkLowStock() {
        String adminEmail = appProperties.getAdminNotificationEmail();
        if (adminEmail == null || adminEmail.isBlank()) return;

        int threshold = appProperties.getAutomation().getLowStockThreshold();
        Instant alertCutoff = Instant.now().minus(java.time.Duration.ofHours(24));
        List<InventoryLevel> lowLevels = inventoryLevelRepo.findLowStock(threshold, alertCutoff);
        if (lowLevels.isEmpty()) return;

        log.info("Found {} low-stock inventory level(s)", lowLevels.size());

        Set<UUID> inventoryItemIds = lowLevels.stream()
            .map(il -> il.getInventoryItem().getId()).collect(Collectors.toSet());
        Map<UUID, InventoryItem> itemsById = inventoryItemRepo.findAllById(inventoryItemIds).stream()
            .collect(Collectors.toMap(InventoryItem::getId, ii -> ii));

        Set<UUID> variantIds = itemsById.values().stream()
            .map(InventoryItem::getVariantId).collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantsById = variantRepo.findAllById(variantIds).stream()
            .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        Set<UUID> productIds = variantsById.values().stream()
            .map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<UUID, String> titleByProductId = productRepo.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Product::getTitle));

        List<String> itemLines = lowLevels.stream().map(level -> {
            InventoryItem invItem = itemsById.get(level.getInventoryItem().getId());
            if (invItem == null) return null;
            ProductVariant variant = variantsById.get(invItem.getVariantId());
            if (variant == null) return null;
            String productTitle = titleByProductId.getOrDefault(variant.getProductId(), "Unknown");
            int available = level.getQuantityOnHand() - level.getQuantityCommitted();
            return String.format("%s — %s: %d unit(s) available",
                productTitle, variant.getTitle(), available);
        }).filter(line -> line != null).toList();

        if (!itemLines.isEmpty()) {
            emailService.sendLowStockAlert(adminEmail, itemLines);
            Instant now = Instant.now();
            for (InventoryLevel level : lowLevels) {
                level.setLowStockAlertedAt(now);
                inventoryLevelRepo.save(level);
            }
            log.info("Sent low-stock alert for {} variant(s)", itemLines.size());
        }
    }
}
