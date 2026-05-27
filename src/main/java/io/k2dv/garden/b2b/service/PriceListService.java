package io.k2dv.garden.b2b.service;

import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.model.PriceList;
import io.k2dv.garden.b2b.model.PriceListAdjustmentType;
import io.k2dv.garden.b2b.model.PriceListEntry;
import io.k2dv.garden.b2b.repository.CompanyRepository;
import io.k2dv.garden.b2b.repository.PriceListEntryRepository;
import io.k2dv.garden.b2b.repository.PriceListRepository;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages custom price lists assigned to B2B companies, including volume-tiered
 * per-variant pricing and list-level percentage adjustments. The key method
 * {@link #resolvePrice} is called at cart and order creation time to determine
 * the effective price for a given company, variant, and quantity combination.
 */
@Service
@RequiredArgsConstructor
public class PriceListService {

    private final PriceListRepository priceListRepo;
    private final PriceListEntryRepository entryRepo;
    private final CompanyRepository companyRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;

    /**
     * Creates a new price list for a company, with optional activation window (startsAt/endsAt)
     * and an optional list-level adjustment rule (percentage off or markup) that applies when
     * no per-variant entry matches.
     */
    @Transactional
    public PriceListResponse create(CreatePriceListRequest req) {
        companyRepo.findById(req.companyId())
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));

        validateAdjustment(req.adjustmentType(), req.adjustmentValue());

        PriceList pl = new PriceList();
        pl.setCompanyId(req.companyId());
        pl.setName(req.name());
        pl.setCurrency(req.currency() != null ? req.currency() : "USD");
        pl.setPriority(req.priority() != null ? req.priority() : 0);
        pl.setStartsAt(req.startsAt());
        pl.setEndsAt(req.endsAt());
        pl.setAdjustmentType(req.adjustmentType());
        pl.setAdjustmentValue(req.adjustmentValue());
        return toResponse(priceListRepo.save(pl));
    }

    @Transactional(readOnly = true)
    public List<PriceListResponse> listByCompany(UUID companyId) {
        return priceListRepo.findByCompanyIdOrderByPriorityDesc(companyId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PriceListResponse getById(UUID id) {
        return toResponse(requirePriceList(id));
    }

    /**
     * Updates all mutable fields of a price list including its activation window and
     * list-level adjustment rule; validates that adjustmentType and adjustmentValue are
     * both set or both null.
     */
    @Transactional
    public PriceListResponse update(UUID id, UpdatePriceListRequest req) {
        validateAdjustment(req.adjustmentType(), req.adjustmentValue());

        PriceList pl = requirePriceList(id);
        pl.setName(req.name());
        if (req.currency() != null) pl.setCurrency(req.currency());
        if (req.priority() != null) pl.setPriority(req.priority());
        pl.setStartsAt(req.startsAt());
        pl.setEndsAt(req.endsAt());
        pl.setAdjustmentType(req.adjustmentType());
        pl.setAdjustmentValue(req.adjustmentValue());
        return toResponse(priceListRepo.save(pl));
    }

    @Transactional
    public void delete(UUID id) {
        requirePriceList(id);
        priceListRepo.deleteById(id);
    }

    /**
     * Creates or updates a volume-tier entry for a specific variant on a price list.
     * The combination of (priceListId, variantId, minQty) is the natural key; if a matching
     * entry already exists, its price is overwritten.
     */
    @Transactional
    public PriceListEntryResponse upsertEntry(UUID priceListId, UUID variantId, UpsertPriceListEntryRequest req) {
        requirePriceList(priceListId);
        variantRepo.findById(variantId)
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));

        PriceListEntry entry = entryRepo
            .findByPriceListIdAndVariantIdAndMinQty(priceListId, variantId, req.minQty())
            .orElseGet(PriceListEntry::new);

        entry.setPriceListId(priceListId);
        entry.setVariantId(variantId);
        entry.setPrice(req.price());
        entry.setMinQty(req.minQty());
        return toEntryResponse(entryRepo.save(entry));
    }

    @Transactional
    public void deleteEntry(UUID priceListId, UUID variantId) {
        requirePriceList(priceListId);
        entryRepo.deleteByPriceListIdAndVariantId(priceListId, variantId);
    }

    @Transactional(readOnly = true)
    public List<PriceListEntryResponse> listEntries(UUID priceListId) {
        requirePriceList(priceListId);
        return entryRepo.findByPriceListIdOrderByMinQtyAsc(priceListId)
            .stream().map(this::toEntryResponse).toList();
    }

    /**
     * Resolves the effective unit price for a company/variant/quantity combination by
     * evaluating all currently active price lists in priority order. Checks per-variant
     * volume tiers first; falls back to list-level percentage adjustment rules; and
     * returns the catalog price when no B2B pricing applies. This is the primary
     * entry point called at cart and order time.
     */
    @Transactional(readOnly = true)
    public ResolvedPriceResponse resolvePrice(UUID companyId, UUID variantId, int qty) {
        companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        ProductVariant variant = variantRepo.findById(variantId)
            .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND", "Variant not found"));

        List<PriceList> activeLists = priceListRepo.findActiveLists(companyId, Instant.now());
        if (!activeLists.isEmpty()) {
            List<UUID> listIds = activeLists.stream().map(PriceList::getId).toList();
            List<PriceListEntry> candidates = entryRepo.findCandidates(listIds, variantId, qty);
            if (!candidates.isEmpty()) {
                // findCandidates returns ordered by minQty DESC — first is best match
                // Break ties by price list priority (activeLists ordered by priority DESC)
                PriceListEntry best = pickBestEntry(candidates, activeLists);
                PriceList matchedList = activeLists.stream()
                    .filter(pl -> pl.getId().equals(best.getPriceListId()))
                    .findFirst().orElseThrow();
                return new ResolvedPriceResponse(
                    variantId, companyId, qty,
                    best.getPrice(), matchedList.getCurrency(),
                    matchedList.getId(), true
                );
            }

            // No per-variant entry — check for a list-level adjustment rule (highest priority first)
            for (PriceList pl : activeLists) {
                if (pl.getAdjustmentType() != null) {
                    BigDecimal adjusted = applyAdjustment(variant.getPrice(), pl.getAdjustmentType(), pl.getAdjustmentValue());
                    return new ResolvedPriceResponse(
                        variantId, companyId, qty,
                        adjusted, pl.getCurrency(),
                        pl.getId(), true
                    );
                }
            }
        }

        return new ResolvedPriceResponse(
            variantId, companyId, qty,
            variant.getPrice(), "USD", null, false
        );
    }

    /**
     * Returns price list entries visible to a company, enriched with product title, handle,
     * variant title, SKU, and the retail price for comparison. Validates that the price list
     * belongs to the requesting company.
     */
    @Transactional(readOnly = true)
    public List<CustomerPriceEntryResponse> listEntriesForCustomer(UUID priceListId, UUID companyId) {
        PriceList pl = requirePriceList(priceListId);
        if (!pl.getCompanyId().equals(companyId)) {
            throw new NotFoundException("PRICE_LIST_NOT_FOUND", "Price list not found");
        }
        List<PriceListEntry> entries = entryRepo.findByPriceListIdOrderByMinQtyAsc(priceListId);
        if (entries.isEmpty()) return List.of();

        List<UUID> variantIds = entries.stream().map(PriceListEntry::getVariantId).toList();
        Map<UUID, ProductVariant> variantMap = variantRepo.findAllById(variantIds)
            .stream().collect(Collectors.toMap(ProductVariant::getId, v -> v));

        List<UUID> productIds = variantMap.values().stream()
            .map(ProductVariant::getProductId).distinct().toList();
        Map<UUID, Product> productMap = productRepo.findAllById(productIds)
            .stream().collect(Collectors.toMap(Product::getId, p -> p));

        return entries.stream().map(e -> {
            ProductVariant v = variantMap.get(e.getVariantId());
            Product p = v != null ? productMap.get(v.getProductId()) : null;
            return new CustomerPriceEntryResponse(
                e.getVariantId(),
                p != null ? p.getTitle() : null,
                p != null ? p.getHandle() : null,
                v != null ? v.getTitle() : null,
                v != null ? v.getSku() : null,
                v != null ? v.getPrice() : null,
                e.getPrice(),
                e.getMinQty()
            );
        }).toList();
    }

    /**
     * Returns all volume-pricing tiers grouped by variant for a product, filtered to the
     * company's currently active price lists. Used by the storefront to display tiered
     * pricing tables on product detail pages.
     */
    @Transactional(readOnly = true)
    public List<VariantPriceTiersResponse> getProductTiers(UUID companyId, String productHandle) {
        companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        Product product = productRepo.findByHandle(productHandle)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        List<UUID> variantIds = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(product.getId())
            .stream().map(ProductVariant::getId).toList();
        if (variantIds.isEmpty()) return List.of();

        List<PriceList> activeLists = priceListRepo.findActiveLists(companyId, Instant.now());
        if (activeLists.isEmpty()) return List.of();

        List<UUID> listIds = activeLists.stream().map(PriceList::getId).toList();
        List<PriceListEntry> entries = entryRepo.findByPriceListIdsAndVariantIds(listIds, variantIds);

        Map<UUID, List<PriceTierEntry>> tiersMap = new LinkedHashMap<>();
        for (UUID vid : variantIds) tiersMap.put(vid, new ArrayList<>());
        for (PriceListEntry e : entries) {
            tiersMap.computeIfPresent(e.getVariantId(),
                (k, list) -> { list.add(new PriceTierEntry(e.getMinQty(), e.getPrice())); return list; });
        }

        return tiersMap.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(entry -> new VariantPriceTiersResponse(entry.getKey(), entry.getValue()))
            .toList();
    }

    private PriceListEntry pickBestEntry(List<PriceListEntry> candidates, List<PriceList> orderedLists) {
        // Among candidates with equal minQty, prefer the entry in the highest-priority list
        int bestMinQty = candidates.get(0).getMinQty();
        List<PriceListEntry> topTier = candidates.stream()
            .filter(e -> e.getMinQty() == bestMinQty)
            .toList();
        if (topTier.size() == 1) return topTier.get(0);

        for (PriceList pl : orderedLists) {
            for (PriceListEntry e : topTier) {
                if (e.getPriceListId().equals(pl.getId())) return e;
            }
        }
        return candidates.get(0);
    }

    private PriceList requirePriceList(UUID id) {
        return priceListRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("PRICE_LIST_NOT_FOUND", "Price list not found"));
    }

    private void validateAdjustment(PriceListAdjustmentType type, BigDecimal value) {
        if ((type == null) != (value == null)) {
            throw new ValidationException("INVALID_ADJUSTMENT",
                "adjustmentType and adjustmentValue must both be set or both be null");
        }
    }

    private BigDecimal applyAdjustment(BigDecimal base, PriceListAdjustmentType type, BigDecimal value) {
        return switch (type) {
            case PERCENTAGE_OFF ->
                base.multiply(BigDecimal.ONE.subtract(value.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)))
                    .setScale(4, RoundingMode.HALF_UP);
            case MARKUP_PERCENTAGE ->
                base.multiply(BigDecimal.ONE.add(value.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)))
                    .setScale(4, RoundingMode.HALF_UP);
        };
    }

    private PriceListResponse toResponse(PriceList pl) {
        return new PriceListResponse(
            pl.getId(), pl.getCompanyId(), pl.getName(), pl.getCurrency(),
            pl.getPriority(), pl.getStartsAt(), pl.getEndsAt(),
            pl.getAdjustmentType(), pl.getAdjustmentValue(),
            pl.getCreatedAt(), pl.getUpdatedAt()
        );
    }

    private PriceListEntryResponse toEntryResponse(PriceListEntry e) {
        return new PriceListEntryResponse(
            e.getId(), e.getPriceListId(), e.getVariantId(),
            e.getPrice(), e.getMinQty(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
