package io.k2dv.garden.b2b.service;

import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.model.PriceList;
import io.k2dv.garden.b2b.model.PriceListEntry;
import io.k2dv.garden.b2b.repository.CompanyRepository;
import io.k2dv.garden.b2b.repository.PriceListEntryRepository;
import io.k2dv.garden.b2b.repository.PriceListRepository;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PriceListService {

    private final PriceListRepository priceListRepo;
    private final PriceListEntryRepository entryRepo;
    private final CompanyRepository companyRepo;
    private final ProductVariantRepository variantRepo;

    @Transactional
    public PriceListResponse create(CreatePriceListRequest req) {
        companyRepo.findById(req.companyId())
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));

        PriceList pl = new PriceList();
        pl.setCompanyId(req.companyId());
        pl.setName(req.name());
        pl.setCurrency(req.currency() != null ? req.currency() : "USD");
        pl.setPriority(req.priority() != null ? req.priority() : 0);
        pl.setStartsAt(req.startsAt());
        pl.setEndsAt(req.endsAt());
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

    @Transactional
    public PriceListResponse update(UUID id, UpdatePriceListRequest req) {
        PriceList pl = requirePriceList(id);
        pl.setName(req.name());
        if (req.currency() != null) pl.setCurrency(req.currency());
        if (req.priority() != null) pl.setPriority(req.priority());
        pl.setStartsAt(req.startsAt());
        pl.setEndsAt(req.endsAt());
        return toResponse(priceListRepo.save(pl));
    }

    @Transactional
    public void delete(UUID id) {
        requirePriceList(id);
        priceListRepo.deleteById(id);
    }

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
        }

        return new ResolvedPriceResponse(
            variantId, companyId, qty,
            variant.getPrice(), "USD", null, false
        );
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

    private PriceListResponse toResponse(PriceList pl) {
        return new PriceListResponse(
            pl.getId(), pl.getCompanyId(), pl.getName(), pl.getCurrency(),
            pl.getPriority(), pl.getStartsAt(), pl.getEndsAt(),
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
