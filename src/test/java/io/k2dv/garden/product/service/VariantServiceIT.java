package io.k2dv.garden.product.service;

import io.k2dv.garden.inventory.model.FulfillmentType;
import io.k2dv.garden.inventory.model.InventoryPolicy;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.product.dto.CreateOptionRequest;
import io.k2dv.garden.product.dto.CreateOptionValueRequest;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.UpdateVariantRequest;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariantServiceIT extends AbstractIntegrationTest {

    @Autowired VariantService variantService;
    @Autowired OptionService optionService;
    @Autowired ProductService productService;
    @Autowired ProductVariantRepository variantRepo;
    @Autowired InventoryItemRepository inventoryRepo;

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_productNotFound_throwsNotFoundException() {
        var req = new CreateVariantRequest(new BigDecimal("9.99"), null, null, null, null, null, List.of());
        assertThatThrownBy(() -> variantService.create(UUID.randomUUID(), req))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_compareAtPriceEqualToPrice_throwsValidationException() {
        var product = productService.create(new CreateProductRequest("Widget", null, null, null, null, List.of(), null, null));
        var req = new CreateVariantRequest(
            new BigDecimal("10.00"), new BigDecimal("10.00"), null, null, null, null, List.of());
        assertThatThrownBy(() -> variantService.create(product.id(), req))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_compareAtPriceLessThanPrice_throwsValidationException() {
        var product = productService.create(new CreateProductRequest("Widget", null, null, null, null, List.of(), null, null));
        var req = new CreateVariantRequest(
            new BigDecimal("10.00"), new BigDecimal("5.00"), null, null, null, null, List.of());
        assertThatThrownBy(() -> variantService.create(product.id(), req))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_invalidOptionValueId_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Widget", null, null, null, null, List.of(), null, null));
        var req = new CreateVariantRequest(
            new BigDecimal("9.99"), null, null, null, null, null, List.of(UUID.randomUUID()));
        assertThatThrownBy(() -> variantService.create(product.id(), req))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_noOptionValues_defaultTitle() {
        var product = productService.create(new CreateProductRequest("Mug", null, null, null, null, List.of(), null, null));
        var req = new CreateVariantRequest(new BigDecimal("12.00"), null, null, null, null, null, List.of());
        var variant = variantService.create(product.id(), req);

        assertThat(variant.title()).isEqualTo("Default Title");
        assertThat(inventoryRepo.findByVariantId(variant.id())).isPresent();
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_patchesFields() {
        var product = productService.create(new CreateProductRequest("Bag", null, null, null, null, List.of(), null, null));
        var created = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("20.00"), null, null, null, null, null, List.of()));

        var req = new UpdateVariantRequest(
            new BigDecimal("25.00"), new BigDecimal("30.00"), "BAG-SKU", "1234567890",
            new BigDecimal("0.5"), "kg", FulfillmentType.PRE_ORDER, InventoryPolicy.CONTINUE, 7);
        var updated = variantService.update(product.id(), created.id(), req);

        assertThat(updated.price()).isEqualByComparingTo("25.00");
        assertThat(updated.compareAtPrice()).isEqualByComparingTo("30.00");
        assertThat(updated.sku()).isEqualTo("BAG-SKU");
        assertThat(updated.barcode()).isEqualTo("1234567890");
        assertThat(updated.fulfillmentType()).isEqualTo(FulfillmentType.PRE_ORDER);
        assertThat(updated.inventoryPolicy()).isEqualTo(InventoryPolicy.CONTINUE);
        assertThat(updated.leadTimeDays()).isEqualTo(7);
    }

    @Test
    void update_wrongProductId_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Lamp", null, null, null, null, List.of(), null, null));
        var variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("15.00"), null, null, null, null, null, List.of()));

        var req = new UpdateVariantRequest(new BigDecimal("20.00"), null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> variantService.update(UUID.randomUUID(), variant.id(), req))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_compareAtPriceValidatedAgainstEffectivePrice() {
        var product = productService.create(new CreateProductRequest("Chair", null, null, null, null, List.of(), null, null));
        var variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("100.00"), null, null, null, null, null, List.of()));

        // New price 80, compareAtPrice 70 — 70 <= 80 should throw
        var req = new UpdateVariantRequest(
            new BigDecimal("80.00"), new BigDecimal("70.00"), null, null, null, null, null, null, null);
        assertThatThrownBy(() -> variantService.update(product.id(), variant.id(), req))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void update_compareAtPriceValidatedAgainstExistingPriceWhenNoPriceInRequest() {
        var product = productService.create(new CreateProductRequest("Desk", null, null, null, null, List.of(), null, null));
        var variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("50.00"), null, null, null, null, null, List.of()));

        // No price in request; existing price is 50.00; compareAtPrice 40 <= 50 should throw
        var req = new UpdateVariantRequest(
            null, new BigDecimal("40.00"), null, null, null, null, null, null, null);
        assertThatThrownBy(() -> variantService.update(product.id(), variant.id(), req))
            .isInstanceOf(ValidationException.class);
    }

    // ── softDelete ────────────────────────────────────────────────────────────

    @Test
    void softDelete_wrongProductId_throwsNotFoundException() {
        var product = productService.create(new CreateProductRequest("Fan", null, null, null, null, List.of(), null, null));
        var variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("30.00"), null, null, null, null, null, List.of()));

        assertThatThrownBy(() -> variantService.softDelete(UUID.randomUUID(), variant.id()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void softDelete_setsDeletedAt() {
        var product = productService.create(new CreateProductRequest("Clock", null, null, null, null, List.of(), null, null));
        var variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("45.00"), null, null, null, null, null, List.of()));

        variantService.softDelete(product.id(), variant.id());

        var found = variantRepo.findByIdAndDeletedAtIsNull(variant.id());
        assertThat(found).isEmpty();
    }

    // ── getInventoryForProduct ────────────────────────────────────────────────

    @Test
    void getInventoryForProduct_returnsOneEntryPerVariant() {
        var product = productService.create(new CreateProductRequest("Shelf", null, null, null, null, List.of(), null, null));
        variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("10.00"), null, "S1", null, null, null, List.of()));
        variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("20.00"), null, "S2", null, null, null, List.of()));

        var inventory = variantService.getInventoryForProduct(product.id());

        assertThat(inventory).hasSize(2);
        assertThat(inventory).allMatch(i -> i.requiresShipping());
    }

    @Test
    void getInventoryForProduct_excludesSoftDeletedVariants() {
        var product = productService.create(new CreateProductRequest("Rack", null, null, null, null, List.of(), null, null));
        var v1 = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("10.00"), null, null, null, null, null, List.of()));
        variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("20.00"), null, null, null, null, null, List.of()));
        variantService.softDelete(product.id(), v1.id());

        var inventory = variantService.getInventoryForProduct(product.id());

        // Only the non-deleted variant's inventory item is returned
        assertThat(inventory).hasSize(1);
    }

    // ── buildTitle (package-private helper) ───────────────────────────────────

    @Test
    void buildTitle_emptyList_returnsDefaultTitle() {
        assertThat(variantService.buildTitle(List.of())).isEqualTo("Default Title");
    }

    @Test
    void buildTitle_multipleValues_joinsWithSlash() {
        var product = productService.create(new CreateProductRequest("Tee", null, null, null, null, List.of(), null, null));
        var colorOpt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
        var red = optionService.createOptionValue(product.id(), colorOpt.id(), new CreateOptionValueRequest("Red", 1));
        var sizeOpt = optionService.createOption(product.id(), new CreateOptionRequest("Size", 2));
        var sm = optionService.createOptionValue(product.id(), sizeOpt.id(), new CreateOptionValueRequest("Small", 1));

        var variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("9.99"), null, null, null, null, null,
                List.of(red.id(), sm.id())));

        assertThat(variant.title()).isEqualTo("Red / Small");
    }
}
