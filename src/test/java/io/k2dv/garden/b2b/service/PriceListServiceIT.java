package io.k2dv.garden.b2b.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.model.PriceListAdjustmentType;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceListServiceIT extends AbstractIntegrationTest {

    @Autowired PriceListService priceListService;
    @Autowired CompanyService companyService;
    @Autowired ProductService productService;
    @Autowired VariantService variantService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private UUID companyId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "pl-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();

        companyId = companyService.create(userId,
            new CreateCompanyRequest("Test Co", null, null, null, null, null, null, null, null)).id();

        AdminProductResponse product = productService.create(
            new CreateProductRequest("Widget", null, null, null, null, List.of(), null, null));
        AdminVariantResponse variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("100.00"), null, null, null, null, null, List.of()));
        variantId = variant.id();
    }

    private CreatePriceListRequest req(String name, String currency, int priority) {
        return new CreatePriceListRequest(companyId, name, currency, priority, null, null, null, null);
    }

    private CreatePriceListRequest reqWithRule(String name, PriceListAdjustmentType type, BigDecimal value) {
        return new CreatePriceListRequest(companyId, name, "USD", 0, null, null, type, value);
    }

    // --- CRUD ---

    @Test
    void create_thenGetById_returnsCorrectList() {
        PriceListResponse pl = priceListService.create(
            new CreatePriceListRequest(companyId, "Contract 2026", "USD", 10, null, null, null, null));

        PriceListResponse found = priceListService.getById(pl.id());
        assertThat(found.name()).isEqualTo("Contract 2026");
        assertThat(found.currency()).isEqualTo("USD");
        assertThat(found.priority()).isEqualTo(10);
        assertThat(found.companyId()).isEqualTo(companyId);
        assertThat(found.adjustmentType()).isNull();
        assertThat(found.adjustmentValue()).isNull();
    }

    @Test
    void listByCompany_returnsAllLists() {
        priceListService.create(req("List A", "USD", 1));
        priceListService.create(req("List B", "USD", 2));

        List<PriceListResponse> lists = priceListService.listByCompany(companyId);
        assertThat(lists).hasSizeGreaterThanOrEqualTo(2);
        assertThat(lists).extracting(PriceListResponse::name).contains("List A", "List B");
    }

    @Test
    void listByCompany_orderedByPriorityDesc() {
        priceListService.create(req("Low",  "USD", 1));
        priceListService.create(req("High", "USD", 9));
        priceListService.create(req("Mid",  "USD", 5));

        List<PriceListResponse> lists = priceListService.listByCompany(companyId);
        List<Integer> priorities = lists.stream().map(PriceListResponse::priority).toList();
        assertThat(priorities).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
    }

    @Test
    void update_changesFields() {
        PriceListResponse pl = priceListService.create(req("Old Name", "USD", 0));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end   = Instant.now().plus(30, ChronoUnit.DAYS);
        PriceListResponse updated = priceListService.update(pl.id(),
            new UpdatePriceListRequest("New Name", "EUR", 5, start, end, null, null));

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.currency()).isEqualTo("EUR");
        assertThat(updated.priority()).isEqualTo(5);
        assertThat(updated.startsAt()).isNotNull();
        assertThat(updated.endsAt()).isNotNull();
    }

    @Test
    void delete_thenGetById_throwsNotFound() {
        PriceListResponse pl = priceListService.create(req("Temp", "USD", 0));
        priceListService.delete(pl.id());

        assertThatThrownBy(() -> priceListService.getById(pl.id()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getById_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> priceListService.getById(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    // --- Entries ---

    @Test
    void upsertEntry_thenListEntries_containsEntry() {
        PriceListResponse pl = priceListService.create(req("Test List", "USD", 0));

        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("75.00"), 1));

        List<PriceListEntryResponse> entries = priceListService.listEntries(pl.id());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).price()).isEqualByComparingTo("75.00");
        assertThat(entries.get(0).minQty()).isEqualTo(1);
    }

    @Test
    void upsertEntry_sameVariantAndMinQty_updatesPrice() {
        PriceListResponse pl = priceListService.create(req("Test List", "USD", 0));

        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("80.00"), 1));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("70.00"), 1));

        List<PriceListEntryResponse> entries = priceListService.listEntries(pl.id());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).price()).isEqualByComparingTo("70.00");
    }

    @Test
    void upsertEntry_differentMinQty_createsVolumeTiers() {
        PriceListResponse pl = priceListService.create(req("Volume List", "USD", 0));

        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("90.00"), 1));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("80.00"), 10));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("70.00"), 50));

        List<PriceListEntryResponse> entries = priceListService.listEntries(pl.id());
        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(PriceListEntryResponse::minQty).containsExactly(1, 10, 50);
    }

    @Test
    void deleteEntry_removesAllTiersForVariant() {
        PriceListResponse pl = priceListService.create(req("Test List", "USD", 0));

        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("80.00"), 1));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("70.00"), 10));

        priceListService.deleteEntry(pl.id(), variantId);

        assertThat(priceListService.listEntries(pl.id())).isEmpty();
    }

    // --- resolvePrice ---

    @Test
    void resolvePrice_noActiveList_returnsCatalogPrice() {
        ResolvedPriceResponse resolved = priceListService.resolvePrice(companyId, variantId, 1);

        assertThat(resolved.contractPrice()).isFalse();
        assertThat(resolved.price()).isEqualByComparingTo("100.00");
        assertThat(resolved.priceListId()).isNull();
    }

    @Test
    void resolvePrice_activeListWithEntry_returnsContractPrice() {
        PriceListResponse pl = priceListService.create(req("Contract", "USD", 0));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("55.00"), 1));

        ResolvedPriceResponse resolved = priceListService.resolvePrice(companyId, variantId, 1);

        assertThat(resolved.contractPrice()).isTrue();
        assertThat(resolved.price()).isEqualByComparingTo("55.00");
        assertThat(resolved.priceListId()).isEqualTo(pl.id());
    }

    @Test
    void resolvePrice_volumeTier_picksHighestMatchingMinQty() {
        PriceListResponse pl = priceListService.create(req("Volume", "USD", 0));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("90.00"), 1));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("80.00"), 10));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("70.00"), 50));

        assertThat(priceListService.resolvePrice(companyId, variantId, 5).price())
            .isEqualByComparingTo("90.00");
        assertThat(priceListService.resolvePrice(companyId, variantId, 10).price())
            .isEqualByComparingTo("80.00");
        assertThat(priceListService.resolvePrice(companyId, variantId, 100).price())
            .isEqualByComparingTo("70.00");
    }

    @Test
    void resolvePrice_expiredList_fallsBackToCatalog() {
        Instant past = Instant.now().minus(10, ChronoUnit.DAYS);
        PriceListResponse pl = priceListService.create(
            new CreatePriceListRequest(companyId, "Expired", "USD", 0,
                past.minus(20, ChronoUnit.DAYS), past, null, null));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("10.00"), 1));

        ResolvedPriceResponse resolved = priceListService.resolvePrice(companyId, variantId, 1);
        assertThat(resolved.contractPrice()).isFalse();
        assertThat(resolved.price()).isEqualByComparingTo("100.00");
    }

    @Test
    void resolvePrice_futureList_fallsBackToCatalog() {
        Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
        PriceListResponse pl = priceListService.create(
            new CreatePriceListRequest(companyId, "Future", "USD", 0,
                future, future.plus(30, ChronoUnit.DAYS), null, null));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("10.00"), 1));

        ResolvedPriceResponse resolved = priceListService.resolvePrice(companyId, variantId, 1);
        assertThat(resolved.contractPrice()).isFalse();
    }

    @Test
    void resolvePrice_higherPriorityListWins() {
        PriceListResponse low  = priceListService.create(req("Low Pri",  "USD", 1));
        PriceListResponse high = priceListService.create(req("High Pri", "USD", 10));

        priceListService.upsertEntry(low.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("60.00"), 1));
        priceListService.upsertEntry(high.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("50.00"), 1));

        ResolvedPriceResponse resolved = priceListService.resolvePrice(companyId, variantId, 1);
        assertThat(resolved.price()).isEqualByComparingTo("50.00");
        assertThat(resolved.priceListId()).isEqualTo(high.id());
    }

    @Test
    void resolvePrice_unknownCompany_throwsNotFound() {
        assertThatThrownBy(() -> priceListService.resolvePrice(UUID.randomUUID(), variantId, 1))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resolvePrice_unknownVariant_throwsNotFound() {
        assertThatThrownBy(() -> priceListService.resolvePrice(companyId, UUID.randomUUID(), 1))
            .isInstanceOf(NotFoundException.class);
    }

    // --- Adjustment rules ---

    @Test
    void create_withPercentageOffRule_persistsAndReturnsRule() {
        PriceListResponse pl = priceListService.create(
            reqWithRule("10% Off Everything", PriceListAdjustmentType.PERCENTAGE_OFF, new BigDecimal("10")));

        assertThat(pl.adjustmentType()).isEqualTo(PriceListAdjustmentType.PERCENTAGE_OFF);
        assertThat(pl.adjustmentValue()).isEqualByComparingTo("10");
    }

    @Test
    void resolvePrice_percentageOffRule_appliesWhenNoEntry() {
        // 10% off a $100 base → $90
        priceListService.create(
            reqWithRule("10% Off", PriceListAdjustmentType.PERCENTAGE_OFF, new BigDecimal("10")));

        ResolvedPriceResponse resolved = priceListService.resolvePrice(companyId, variantId, 1);

        assertThat(resolved.contractPrice()).isTrue();
        assertThat(resolved.price()).isEqualByComparingTo("90.0000");
    }

    @Test
    void resolvePrice_markupPercentageRule_appliesWhenNoEntry() {
        // 15% markup on $100 base → $115
        priceListService.create(
            reqWithRule("15% Markup", PriceListAdjustmentType.MARKUP_PERCENTAGE, new BigDecimal("15")));

        ResolvedPriceResponse resolved = priceListService.resolvePrice(companyId, variantId, 1);

        assertThat(resolved.contractPrice()).isTrue();
        assertThat(resolved.price()).isEqualByComparingTo("115.0000");
    }

    @Test
    void resolvePrice_explicitEntryBeatsRule() {
        PriceListResponse pl = priceListService.create(
            reqWithRule("10% Off", PriceListAdjustmentType.PERCENTAGE_OFF, new BigDecimal("10")));
        // explicit entry at $55 — should win over the computed $90
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("55.00"), 1));

        ResolvedPriceResponse resolved = priceListService.resolvePrice(companyId, variantId, 1);

        assertThat(resolved.price()).isEqualByComparingTo("55.00");
    }

    @Test
    void resolvePrice_higherPriorityRuleWins_overLowerPriorityRule() {
        // priority 1 list: 5% off  → $95
        // priority 10 list: 20% off → $80 (wins)
        priceListService.create(new CreatePriceListRequest(companyId, "Low Rule", "USD", 1, null, null,
            PriceListAdjustmentType.PERCENTAGE_OFF, new BigDecimal("5")));
        priceListService.create(new CreatePriceListRequest(companyId, "High Rule", "USD", 10, null, null,
            PriceListAdjustmentType.PERCENTAGE_OFF, new BigDecimal("20")));

        ResolvedPriceResponse resolved = priceListService.resolvePrice(companyId, variantId, 1);

        assertThat(resolved.price()).isEqualByComparingTo("80.0000");
    }

    @Test
    void update_canClearAdjustmentRule() {
        PriceListResponse pl = priceListService.create(
            reqWithRule("10% Off", PriceListAdjustmentType.PERCENTAGE_OFF, new BigDecimal("10")));

        PriceListResponse cleared = priceListService.update(pl.id(),
            new UpdatePriceListRequest("10% Off", "USD", 0, null, null, null, null));

        assertThat(cleared.adjustmentType()).isNull();
        assertThat(cleared.adjustmentValue()).isNull();
    }

    @Test
    void create_adjustmentTypeWithoutValue_throwsValidation() {
        assertThatThrownBy(() -> priceListService.create(
            new CreatePriceListRequest(companyId, "Bad", "USD", 0, null, null,
                PriceListAdjustmentType.PERCENTAGE_OFF, null)))
            .isInstanceOf(ValidationException.class);
    }

    // --- listEntriesForCustomer ---

    @Test
    void listEntriesForCustomer_returnsEnrichedEntries() {
        PriceListResponse pl = priceListService.create(req("Customer View", "USD", 0));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("75.00"), 1));

        List<CustomerPriceEntryResponse> entries =
            priceListService.listEntriesForCustomer(pl.id(), companyId);

        assertThat(entries).hasSize(1);
        CustomerPriceEntryResponse entry = entries.get(0);
        assertThat(entry.variantId()).isEqualTo(variantId);
        assertThat(entry.contractPrice()).isEqualByComparingTo("75.00");
        assertThat(entry.retailPrice()).isEqualByComparingTo("100.00");
        assertThat(entry.minQty()).isEqualTo(1);
        assertThat(entry.productTitle()).isEqualTo("Widget");
    }

    @Test
    void listEntriesForCustomer_volumeTiers_allReturned() {
        PriceListResponse pl = priceListService.create(req("Volume", "USD", 0));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("90.00"), 1));
        priceListService.upsertEntry(pl.id(), variantId,
            new UpsertPriceListEntryRequest(new BigDecimal("80.00"), 10));

        List<CustomerPriceEntryResponse> entries =
            priceListService.listEntriesForCustomer(pl.id(), companyId);

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(CustomerPriceEntryResponse::minQty).containsExactly(1, 10);
    }

    @Test
    void listEntriesForCustomer_wrongCompany_throwsNotFound() {
        PriceListResponse pl = priceListService.create(req("Private", "USD", 0));

        UUID otherCompanyId = companyService.create(userId,
            new CreateCompanyRequest("Other Co", null, null, null, null, null, null, null, null)).id();

        assertThatThrownBy(() -> priceListService.listEntriesForCustomer(pl.id(), otherCompanyId))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listEntriesForCustomer_emptyList_returnsEmpty() {
        PriceListResponse pl = priceListService.create(req("Empty", "USD", 0));

        List<CustomerPriceEntryResponse> entries =
            priceListService.listEntriesForCustomer(pl.id(), companyId);
        assertThat(entries).isEmpty();
    }

    @Test
    void getActiveCurrency_noActiveLists_returnsUSD() {
        String currency = priceListService.getActiveCurrency(companyId);
        assertThat(currency).isEqualTo("USD");
    }

    @Test
    void getActiveCurrency_activeList_returnsListCurrency() {
        Instant now = Instant.now();
        priceListService.create(new CreatePriceListRequest(
            companyId, "EUR List", "EUR", 10,
            now.minusSeconds(3600), now.plusSeconds(3600), null, null));

        String currency = priceListService.getActiveCurrency(companyId);
        assertThat(currency).isEqualTo("EUR");
    }

    @Test
    void getActiveCurrency_unknownCompany_throwsNotFound() {
        assertThatThrownBy(() -> priceListService.getActiveCurrency(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }
}
