package io.k2dv.garden.inventory.service;

import io.k2dv.garden.inventory.dto.*;
import io.k2dv.garden.inventory.model.*;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceIT extends AbstractIntegrationTest {

    @Autowired InventoryService inventoryService;
    @Autowired LocationRepository locationRepo;
    @Autowired ProductRepository productRepo;
    @Autowired ProductVariantRepository variantRepo;
    @Autowired VariantService variantService;

    private ProductVariant variant;
    private Location warehouse;
    private Location manufacturer;

    @BeforeEach
    void setup() {
        // product_variants.product_id has a non-deferrable FK to products(id),
        // so we must create a real Product first.
        Product p = new Product();
        p.setTitle("Test Product");
        p.setHandle("test-product-" + UUID.randomUUID().toString().substring(0, 8));
        p = productRepo.save(p);

        // VariantService.create() auto-creates the InventoryItem 1:1 with the variant.
        var variantResp = variantService.create(p.getId(),
            new CreateVariantRequest(BigDecimal.TEN, null, null, null, null, null, List.of()));
        variant = variantRepo.findById(variantResp.id()).orElseThrow();

        Location wh = new Location();
        wh.setName("Warehouse");
        warehouse = locationRepo.save(wh);

        Location mfg = new Location();
        mfg.setName("Manufacturer");
        manufacturer = locationRepo.save(mfg);
    }

    @Test
    void receiveStock_newLevel_createsLevelAndTransaction() {
        var req = new ReceiveStockRequest(warehouse.getId(), 10, "Initial stock");
        var resp = inventoryService.receiveStock(variant.getId(), req);

        assertThat(resp.quantityOnHand()).isEqualTo(10);
        assertThat(resp.locationId()).isEqualTo(warehouse.getId());

        var txns = inventoryService.listTransactions(variant.getId(), warehouse.getId(),
            PageRequest.of(0, 10));
        assertThat(txns.getContent()).hasSize(1);
        assertThat(txns.getContent().get(0).reason()).isEqualTo(InventoryTransactionReason.RECEIVED);
        assertThat(txns.getContent().get(0).quantity()).isEqualTo(10);
    }

    @Test
    void receiveStock_existingLevel_incrementsQty() {
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 5, null));
        var resp = inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 3, null));
        assertThat(resp.quantityOnHand()).isEqualTo(8);
    }

    @Test
    void adjustStock_negativeAllowed_whenPolicyContinue() {
        // Set inventory policy to CONTINUE
        variant.setInventoryPolicy(InventoryPolicy.CONTINUE);
        variantRepo.save(variant);

        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 2, null));
        var req = new AdjustStockRequest(warehouse.getId(), -5, InventoryTransactionReason.ADJUSTED, "oversell");
        var resp = inventoryService.adjustStock(variant.getId(), req);
        assertThat(resp.quantityOnHand()).isEqualTo(-3);
    }

    @Test
    void adjustStock_belowZero_deny_throwsBadRequest() {
        // Default inventoryPolicy is DENY
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 2, null));
        var req = new AdjustStockRequest(warehouse.getId(), -5, InventoryTransactionReason.ADJUSTED, "too many");
        assertThatThrownBy(() -> inventoryService.adjustStock(variant.getId(), req))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void getLevels_returnsAllLocations() {
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 10, null));
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(manufacturer.getId(), 3, null));
        var levels = inventoryService.getLevels(variant.getId());
        assertThat(levels).hasSize(2);
    }

    @Test
    void listTransactions_filterByLocation_returnsPaged() {
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 5, null));
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(manufacturer.getId(), 3, null));

        var page = inventoryService.listTransactions(variant.getId(), warehouse.getId(),
            PageRequest.of(0, 10, Sort.by("createdAt")));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).locationId()).isEqualTo(warehouse.getId());
    }

    @Test
    void listTransactions_allLocations_whenLocationIdNull() {
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(warehouse.getId(), 5, null));
        inventoryService.receiveStock(variant.getId(), new ReceiveStockRequest(manufacturer.getId(), 3, null));

        var page = inventoryService.listTransactions(variant.getId(), null,
            PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void updateVariantFulfillment_persistsAllThreeFields() {
        var req = new UpdateVariantFulfillmentRequest(
            FulfillmentType.MADE_TO_ORDER, InventoryPolicy.CONTINUE, 14);
        var resp = inventoryService.updateVariantFulfillment(variant.getId(), req);
        assertThat(resp.fulfillmentType()).isEqualTo(FulfillmentType.MADE_TO_ORDER);
        assertThat(resp.inventoryPolicy()).isEqualTo(InventoryPolicy.CONTINUE);
        assertThat(resp.leadTimeDays()).isEqualTo(14);
    }
}
