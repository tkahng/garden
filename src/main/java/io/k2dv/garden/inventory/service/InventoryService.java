package io.k2dv.garden.inventory.service;

import io.k2dv.garden.inventory.dto.*;
import io.k2dv.garden.inventory.model.*;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.InventoryTransactionRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.OptionValueLabel;
import io.k2dv.garden.product.model.ProductOption;
import io.k2dv.garden.product.model.ProductOptionValue;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.product.repository.ProductOptionRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepo;
    private final InventoryLevelRepository levelRepo;
    private final InventoryTransactionRepository txnRepo;
    private final LocationRepository locationRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductOptionRepository optionRepo;

    @Transactional(readOnly = true)
    public List<InventoryLevelResponse> getLevels(UUID variantId) {
        InventoryItem item = findItemByVariant(variantId);
        return levelRepo.findByInventoryItemId(item.getId()).stream()
            .map(this::toLevelResponse)
            .toList();
    }

    @Transactional
    public InventoryLevelResponse receiveStock(UUID variantId, ReceiveStockRequest req) {
        InventoryItem item = findItemByVariant(variantId);
        Location location = findLocation(req.locationId());

        InventoryLevel level = levelRepo
            .findByInventoryItemIdAndLocationId(item.getId(), location.getId())
            .orElseGet(() -> {
                InventoryLevel l = new InventoryLevel();
                l.setInventoryItem(item);
                l.setLocation(location);
                return l;
            });
        level.setQuantityOnHand(level.getQuantityOnHand() + req.quantity());
        level = levelRepo.save(level);

        txnRepo.save(new InventoryTransaction(item, location, req.quantity(),
            InventoryTransactionReason.RECEIVED, req.note()));

        return toLevelResponse(level);
    }

    @Transactional
    public InventoryLevelResponse adjustStock(UUID variantId, AdjustStockRequest req) {
        if (req.reason() == InventoryTransactionReason.RECEIVED
                || req.reason() == InventoryTransactionReason.SOLD) {
            throw new ValidationException("INVALID_REASON",
                "Use the receive endpoint for RECEIVED; SOLD is system-managed");
        }

        ProductVariant variant = variantRepo.findById(variantId)
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        InventoryItem item = findItemByVariant(variantId);
        Location location = findLocation(req.locationId());

        InventoryLevel level = levelRepo
            .findByInventoryItemIdAndLocationId(item.getId(), location.getId())
            .orElseGet(() -> {
                InventoryLevel l = new InventoryLevel();
                l.setInventoryItem(item);
                l.setLocation(location);
                return l;
            });

        int newQty = level.getQuantityOnHand() + req.delta();
        if (newQty < 0 && variant.getInventoryPolicy() == InventoryPolicy.DENY) {
            throw new ValidationException("INSUFFICIENT_STOCK",
                "Cannot adjust below 0 when inventory policy is DENY");
        }
        level.setQuantityOnHand(newQty);
        level = levelRepo.save(level);

        txnRepo.save(new InventoryTransaction(item, location, req.delta(), req.reason(), req.note()));

        return toLevelResponse(level);
    }

    @Transactional(readOnly = true)
    public PagedResult<InventoryTransactionResponse> listTransactions(
            UUID variantId, UUID locationId, Pageable pageable) {
        InventoryItem item = findItemByVariant(variantId);
        Page<InventoryTransaction> page = locationId != null
            ? txnRepo.findByInventoryItemIdAndLocationId(item.getId(), locationId, pageable)
            : txnRepo.findByInventoryItemId(item.getId(), pageable);
        return PagedResult.of(page, this::toTxnResponse);
    }

    @Transactional
    public AdminVariantResponse updateVariantFulfillment(UUID variantId, UpdateVariantFulfillmentRequest req) {
        ProductVariant variant = variantRepo.findById(variantId)
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        variant.setFulfillmentType(req.fulfillmentType());
        variant.setInventoryPolicy(req.inventoryPolicy());
        variant.setLeadTimeDays(req.leadTimeDays());
        variantRepo.save(variant);
        return toVariantResponse(variant);
    }

    private InventoryItem findItemByVariant(UUID variantId) {
        return inventoryItemRepo.findByVariantId(variantId)
            .orElseThrow(() -> new NotFoundException("INVENTORY_ITEM_NOT_FOUND",
                "No inventory item found for variant " + variantId));
    }

    private Location findLocation(UUID locationId) {
        return locationRepo.findById(locationId)
            .orElseThrow(() -> new NotFoundException("LOCATION_NOT_FOUND", "Location not found"));
    }

    private InventoryLevelResponse toLevelResponse(InventoryLevel level) {
        return new InventoryLevelResponse(
            level.getId(),
            level.getInventoryItem().getId(),
            level.getLocation().getId(),
            level.getLocation().getName(),
            level.getQuantityOnHand(),
            level.getQuantityCommitted()
        );
    }

    private InventoryTransactionResponse toTxnResponse(InventoryTransaction txn) {
        return new InventoryTransactionResponse(
            txn.getId(),
            txn.getInventoryItem().getId(),
            txn.getLocation().getId(),
            txn.getLocation().getName(),
            txn.getQuantity(),
            txn.getReason(),
            txn.getNote(),
            txn.getCreatedAt()
        );
    }

    private AdminVariantResponse toVariantResponse(ProductVariant v) {
        Set<UUID> optionIds = v.getOptionValues().stream()
            .map(ProductOptionValue::getOptionId).collect(Collectors.toSet());
        Map<UUID, String> optionNamesById = optionRepo.findAllById(optionIds).stream()
            .collect(Collectors.toMap(ProductOption::getId, ProductOption::getName));
        List<OptionValueLabel> labels = v.getOptionValues().stream()
            .map(ov -> new OptionValueLabel(optionNamesById.getOrDefault(ov.getOptionId(), ""), ov.getLabel()))
            .toList();
        return new AdminVariantResponse(v.getId(), v.getTitle(), v.getSku(), v.getBarcode(),
            v.getPrice(), v.getCompareAtPrice(), v.getWeight(), v.getWeightUnit(),
            labels, v.getFulfillmentType(), v.getInventoryPolicy(), v.getLeadTimeDays(), v.getDeletedAt());
    }

    @Transactional
    public void reserveStock(UUID variantId, int quantity) {
        InventoryItem item = inventoryItemRepo.findByVariantId(variantId)
            .orElseThrow(() -> new NotFoundException("INVENTORY_NOT_FOUND",
                "No inventory item for variant " + variantId));

        List<InventoryLevel> levels = levelRepo.findByInventoryItemId(item.getId());
        int totalAvailable = levels.stream()
            .mapToInt(l -> l.getQuantityOnHand() - l.getQuantityCommitted())
            .sum();

        if (totalAvailable < quantity) {
            throw new ValidationException("INSUFFICIENT_STOCK",
                "Insufficient stock for variant " + variantId + ": available=" + totalAvailable + ", requested=" + quantity);
        }

        int remaining = quantity;
        for (InventoryLevel level : levels) {
            if (remaining <= 0) break;
            int available = level.getQuantityOnHand() - level.getQuantityCommitted();
            int toCommit = Math.min(available, remaining);
            if (toCommit > 0) {
                level.setQuantityCommitted(level.getQuantityCommitted() + toCommit);
                levelRepo.save(level);
                remaining -= toCommit;
            }
        }
    }

    @Transactional
    public void releaseReservation(UUID variantId, int quantity) {
        InventoryItem item = inventoryItemRepo.findByVariantId(variantId)
            .orElseThrow(() -> new NotFoundException("INVENTORY_NOT_FOUND",
                "No inventory item for variant " + variantId));

        List<InventoryLevel> levels = levelRepo.findByInventoryItemId(item.getId());
        int remaining = quantity;
        for (InventoryLevel level : levels) {
            if (remaining <= 0) break;
            int toRelease = Math.min(level.getQuantityCommitted(), remaining);
            if (toRelease > 0) {
                level.setQuantityCommitted(level.getQuantityCommitted() - toRelease);
                levelRepo.save(level);
                remaining -= toRelease;
            }
        }
    }

    @Transactional
    public void confirmSale(UUID variantId, int quantity) {
        InventoryItem item = inventoryItemRepo.findByVariantId(variantId)
            .orElseThrow(() -> new NotFoundException("INVENTORY_NOT_FOUND",
                "No inventory item for variant " + variantId));

        List<InventoryLevel> levels = levelRepo.findByInventoryItemId(item.getId());
        int remaining = quantity;
        for (InventoryLevel level : levels) {
            if (remaining <= 0) break;
            int toDeduct = Math.min(level.getQuantityCommitted(), remaining);
            if (toDeduct > 0) {
                level.setQuantityOnHand(level.getQuantityOnHand() - toDeduct);
                level.setQuantityCommitted(level.getQuantityCommitted() - toDeduct);
                levelRepo.save(level);
                txnRepo.save(new InventoryTransaction(
                    item, level.getLocation(), -toDeduct, InventoryTransactionReason.SOLD, null));
                remaining -= toDeduct;
            }
        }
    }
}
