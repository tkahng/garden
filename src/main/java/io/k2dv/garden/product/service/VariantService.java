package io.k2dv.garden.product.service;

import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.InventoryItemResponse;
import io.k2dv.garden.product.dto.OptionValueLabel;
import io.k2dv.garden.product.dto.UpdateVariantRequest;
import io.k2dv.garden.inventory.model.InventoryItem;
import io.k2dv.garden.product.model.ProductOption;
import io.k2dv.garden.product.model.ProductOptionValue;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.product.repository.ProductOptionRepository;
import io.k2dv.garden.product.repository.ProductOptionValueRepository;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VariantService {

    private final ProductVariantRepository variantRepo;
    private final ProductOptionValueRepository optionValueRepo;
    private final ProductOptionRepository optionRepo;
    private final InventoryItemRepository inventoryRepo;
    private final ProductRepository productRepo;

    @Transactional
    public AdminVariantResponse create(UUID productId, CreateVariantRequest req) {
        productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        if (req.compareAtPrice() != null && req.price() != null
                && req.compareAtPrice().compareTo(req.price()) <= 0) {
            throw new ValidationException("INVALID_COMPARE_PRICE", "Compare-at price must be greater than price");
        }

        // Resolve option values
        List<ProductOptionValue> optionValues;
        if (req.optionValueIds() == null || req.optionValueIds().isEmpty()) {
            optionValues = List.of();
        } else {
            optionValues = optionValueRepo.findAllById(req.optionValueIds());
            if (optionValues.size() != req.optionValueIds().size()) {
                throw new NotFoundException("OPTION_VALUE_NOT_FOUND", "One or more option values not found");
            }
        }

        // Build title
        String title = buildTitle(optionValues);

        ProductVariant v = new ProductVariant();
        v.setProductId(productId);
        v.setTitle(title);
        v.setSku(req.sku());
        v.setBarcode(req.barcode());
        v.setPrice(req.price());
        v.setCompareAtPrice(req.compareAtPrice());
        v.setWeight(req.weight());
        v.setWeightUnit(req.weightUnit());
        v.getOptionValues().addAll(optionValues);
        v = variantRepo.save(v);

        // Auto-create inventory item
        InventoryItem inv = new InventoryItem();
        inv.setVariantId(v.getId());
        inv.setRequiresShipping(true);
        inventoryRepo.save(inv);

        return toResponse(v);
    }

    @Transactional
    public AdminVariantResponse update(UUID productId, UUID variantId, UpdateVariantRequest req) {
        ProductVariant v = variantRepo.findByIdAndDeletedAtIsNull(variantId)
            .filter(vv -> vv.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));

        // Validate compareAtPrice against the effective price (new or existing)
        BigDecimal effectivePrice = req.price() != null ? req.price() : v.getPrice();
        if (req.compareAtPrice() != null && req.compareAtPrice().compareTo(effectivePrice) <= 0) {
            throw new ValidationException("INVALID_COMPARE_PRICE", "Compare-at price must be greater than price");
        }
        if (req.price() != null) v.setPrice(req.price());
        if (req.compareAtPrice() != null) v.setCompareAtPrice(req.compareAtPrice());
        if (req.sku() != null) v.setSku(req.sku());
        if (req.barcode() != null) v.setBarcode(req.barcode());
        if (req.weight() != null) v.setWeight(req.weight());
        if (req.weightUnit() != null) v.setWeightUnit(req.weightUnit());
        return toResponse(variantRepo.save(v));
    }

    @Transactional
    public void softDelete(UUID productId, UUID variantId) {
        ProductVariant v = variantRepo.findByIdAndDeletedAtIsNull(variantId)
            .filter(vv -> vv.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));
        v.setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getInventoryForProduct(UUID productId) {
        List<ProductVariant> variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(productId);
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        return inventoryRepo.findByVariantIdIn(variantIds).stream()
            .map(inv -> new InventoryItemResponse(inv.getId(), inv.getVariantId(), inv.isRequiresShipping()))
            .toList();
    }

    String buildTitle(List<ProductOptionValue> values) {
        if (values.isEmpty()) return "Default Title";
        return values.stream().map(ProductOptionValue::getLabel).reduce((a, b) -> a + " / " + b).orElse("Default Title");
    }

    private AdminVariantResponse toResponse(ProductVariant v) {
        List<ProductOptionValue> ovs = v.getOptionValues();
        Set<UUID> optionIds = ovs.stream().map(ProductOptionValue::getOptionId).collect(Collectors.toSet());
        Map<UUID, String> optionNamesById = optionRepo.findAllById(optionIds).stream()
            .collect(Collectors.toMap(ProductOption::getId, ProductOption::getName));
        List<OptionValueLabel> labels = ovs.stream()
            .map(ov -> new OptionValueLabel(optionNamesById.getOrDefault(ov.getOptionId(), ""), ov.getLabel()))
            .toList();
        return new AdminVariantResponse(v.getId(), v.getTitle(), v.getSku(), v.getBarcode(),
            v.getPrice(), v.getCompareAtPrice(), v.getWeight(), v.getWeightUnit(),
            labels, v.getFulfillmentType(), v.getInventoryPolicy(), v.getLeadTimeDays(), v.getDeletedAt());
    }
}
