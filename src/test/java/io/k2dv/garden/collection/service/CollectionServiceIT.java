package io.k2dv.garden.collection.service;

import io.k2dv.garden.collection.dto.request.*;
import io.k2dv.garden.collection.dto.response.AdminCollectionResponse;
import io.k2dv.garden.collection.model.*;
import io.k2dv.garden.collection.repository.CollectionProductRepository;
import io.k2dv.garden.collection.repository.CollectionRuleRepository;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionServiceIT extends AbstractIntegrationTest {

    @Autowired CollectionService collectionService;
    @Autowired ProductService productService;
    @Autowired CollectionProductRepository cpRepo;
    @Autowired CollectionRuleRepository ruleRepo;

    private AdminCollectionResponse createManual(String title) {
        return collectionService.create(new CreateCollectionRequest(
                title, null, null, CollectionType.MANUAL, false, null));
    }

    private AdminCollectionResponse createAutomated(String title) {
        return collectionService.create(new CreateCollectionRequest(
                title, null, null, CollectionType.AUTOMATED, false, null));
    }

    private AdminProductResponse createProduct(String title, String... tags) {
        return productService.create(new CreateProductRequest(title, null, null, null, null, List.of(tags)));
    }

    // ---- CRUD ----

    @Test
    void create_persistsWithDraftStatus() {
        var resp = createManual("Summer Sale");
        assertThat(resp.title()).isEqualTo("Summer Sale");
        assertThat(resp.handle()).isEqualTo("summer-sale");
        assertThat(resp.status()).isEqualTo(CollectionStatus.DRAFT);
        assertThat(resp.collectionType()).isEqualTo(CollectionType.MANUAL);
    }

    @Test
    void create_duplicateHandle_throwsConflict() {
        createManual("Shirts");
        assertThatThrownBy(() -> createManual("Shirts")).isInstanceOf(ConflictException.class);
    }

    @Test
    void update_changesTitle() {
        var c = createManual("Old Title");
        var updated = collectionService.update(c.id(), new UpdateCollectionRequest("New Title", null, null, null, null));
        assertThat(updated.title()).isEqualTo("New Title");
    }

    @Test
    void changeStatus_activatesCollection() {
        var c = createManual("Promo");
        collectionService.changeStatus(c.id(), new CollectionStatusRequest(CollectionStatus.ACTIVE));
        assertThat(collectionService.getAdmin(c.id()).status()).isEqualTo(CollectionStatus.ACTIVE);
    }

    @Test
    void softDelete_removesFromAdminGet() {
        var c = createManual("Gone");
        collectionService.softDelete(c.id());
        assertThatThrownBy(() -> collectionService.getAdmin(c.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void softDelete_removesCollectionProductsAndRules() {
        var c = createAutomated("Auto");
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
                CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        createProduct("Widget", "sale");
        collectionService.softDelete(c.id());
        assertThat(ruleRepo.findByCollectionIdOrderByCreatedAtAsc(c.id())).isEmpty();
        assertThat(cpRepo.findByCollectionId(c.id())).isEmpty();
    }

    @Test
    void handleUniqueness_duplicateHandleReturnsConflict() {
        collectionService.create(new CreateCollectionRequest("Hats", "hats", null, CollectionType.MANUAL, false, null));
        assertThatThrownBy(() ->
                collectionService.create(new CreateCollectionRequest("Other", "hats", null, CollectionType.MANUAL, false, null))
        ).isInstanceOf(ConflictException.class);
    }

    // ---- Manual product membership ----

    @Test
    void addProduct_appendsWithPosition() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        var cp = collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        assertThat(cp.productId()).isEqualTo(p.id());
        assertThat(cp.position()).isEqualTo(1);
    }

    @Test
    void addProduct_secondProduct_positionIncremented() {
        var c = createManual("Tops");
        var p1 = createProduct("T-Shirt");
        var p2 = createProduct("Tank Top");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p1.id()));
        var cp2 = collectionService.addProduct(c.id(), new AddCollectionProductRequest(p2.id()));
        assertThat(cp2.position()).isEqualTo(2);
    }

    @Test
    void addProduct_duplicate_throwsConflict() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        assertThatThrownBy(() ->
                collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()))
        ).isInstanceOf(ConflictException.class);
    }

    @Test
    void removeProduct_removesMembership() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        collectionService.removeProduct(c.id(), p.id());
        assertThat(cpRepo.findByCollectionIdAndProductId(c.id(), p.id())).isEmpty();
    }

    @Test
    void removeProduct_notMember_throwsNotFound() {
        var c = createManual("Tops");
        assertThatThrownBy(() -> collectionService.removeProduct(c.id(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatePosition_setsVerbatim() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        var updated = collectionService.updateProductPosition(c.id(), p.id(),
                new UpdateCollectionProductPositionRequest(99));
        assertThat(updated.position()).isEqualTo(99);
    }

    @Test
    void addProduct_toAutomatedCollection_throwsValidation() {
        var c = createAutomated("Auto");
        var p = createProduct("Widget");
        assertThatThrownBy(() -> collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void removeProduct_fromAutomatedCollection_throwsValidation() {
        var c = createAutomated("Auto");
        var p = createProduct("Widget");
        assertThatThrownBy(() -> collectionService.removeProduct(c.id(), p.id()))
                .isInstanceOf(ValidationException.class);
    }

    // ---- Rules and automated membership ----

    @Test
    void addRule_triggersSync_addsQualifyingActiveProducts() {
        var c = createAutomated("Sale Items");
        var p = createProduct("Sale Widget", "sale");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        // Rule added AFTER product is ACTIVE — sync picks it up
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
                CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();
    }

    @Test
    void addRule_doesNotSync_draftProducts() {
        var c = createAutomated("Sale Items");
        createProduct("Draft Widget", "sale"); // DRAFT — not synced
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
                CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(cpRepo.findByCollectionId(c.id())).isEmpty();
    }

    @Test
    void deleteRule_triggersSync_removesNonQualifyingProducts() {
        var c = createAutomated("Sale Items");
        var p = createProduct("Sale Widget", "sale");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        var rule = collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
                CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();
        collectionService.deleteRule(c.id(), rule.id());
        assertThat(cpRepo.findByCollectionId(c.id())).isEmpty();
    }

    @Test
    void productTagUpdate_triggersMembershipSync() {
        var c = createAutomated("Sale Items");
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
                CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        var p = createProduct("Widget"); // no tags
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();
        // Update tags — triggers syncCollectionsForProduct
        productService.update(p.id(), new UpdateProductRequest(null, null, null, null, null, null, List.of("sale")));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();
    }

    @Test
    void productSoftDelete_removesFromManualCollection() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        productService.softDelete(p.id());
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();
    }

    @Test
    void productSoftDelete_removesFromAutomatedCollection() {
        var c = createAutomated("Sale Items");
        var p = createProduct("Sale Widget", "sale");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        // Add rule AFTER product is ACTIVE so sync includes it
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
                CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();
        productService.softDelete(p.id());
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();
    }

    @Test
    void productArchived_removesFromManualCollection() {
        var c = createManual("Tops");
        var p = createProduct("T-Shirt");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(p.id()));
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ARCHIVED));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();
    }

    @Test
    void productArchived_removesFromAutomatedCollection() {
        var c = createAutomated("Sale Items");
        var p = createProduct("Sale Widget", "sale");
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        // Add rule AFTER product is ACTIVE so sync includes it
        collectionService.addRule(c.id(), new CreateCollectionRuleRequest(
                CollectionRuleField.TAG, CollectionRuleOperator.EQUALS, "sale"));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isTrue();
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ARCHIVED));
        assertThat(cpRepo.existsByCollectionIdAndProductId(c.id(), p.id())).isFalse();
    }

    @Test
    void disjunctiveTrue_storedWithoutError() {
        var req = new CreateCollectionRequest("OR Collection", null, null, CollectionType.AUTOMATED, true, null);
        var resp = collectionService.create(req);
        assertThat(resp.disjunctive()).isTrue();
    }

    // ---- Storefront ----

    @Test
    void storefrontList_returnsOnlyActiveCollections() {
        createManual("Draft Collection");
        var active = createManual("Active Collection");
        collectionService.changeStatus(active.id(), new CollectionStatusRequest(CollectionStatus.ACTIVE));
        var result = collectionService.listStorefront(PageRequest.of(0, 20));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).handle()).isEqualTo("active-collection");
    }

    @Test
    void storefrontByHandle_draftCollection_throwsNotFound() {
        var c = createManual("Draft");
        assertThatThrownBy(() -> collectionService.getByHandle(c.handle()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void storefrontProducts_returnsOnlyActiveProducts() {
        var c = createManual("Tops");
        collectionService.changeStatus(c.id(), new CollectionStatusRequest(CollectionStatus.ACTIVE));
        var draft = createProduct("Draft Tee");
        var active = createProduct("Active Tee");
        productService.changeStatus(active.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(draft.id()));
        collectionService.addProduct(c.id(), new AddCollectionProductRequest(active.id()));
        var result = collectionService.listProductsStorefront(c.handle(), PageRequest.of(0, 20));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Active Tee");
    }
}
