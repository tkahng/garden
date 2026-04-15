package io.k2dv.garden.product.service;

import io.k2dv.garden.blob.model.BlobObject;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductServiceIT extends AbstractIntegrationTest {

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepo;
    @Autowired VariantService variantService;
    @Autowired OptionService optionService;
    @Autowired ProductImageService imageService;
    @Autowired ProductVariantRepository variantRepo;
    @Autowired InventoryItemRepository inventoryRepo;
    @Autowired BlobObjectRepository blobRepo;

    @Test
    void createProduct_persistsWithDraftStatusAndAutoHandle() {
        var req = new CreateProductRequest("My Product!", null, null, null, null, List.of());
        var resp = productService.create(req);

        assertThat(resp.title()).isEqualTo("My Product!");
        assertThat(resp.handle()).isEqualTo("my-product");
        assertThat(resp.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(productRepo.findByIdAndDeletedAtIsNull(resp.id())).isPresent();
    }

    @Test
    void createProduct_withExplicitHandle_usesProvidedHandle() {
        var req = new CreateProductRequest("T-Shirt", null, "custom-tee", null, null, List.of());
        var resp = productService.create(req);
        assertThat(resp.handle()).isEqualTo("custom-tee");
    }

    @Test
    void createProduct_duplicateHandle_throwsConflictException() {
        productService.create(new CreateProductRequest("First", null, null, null, null, List.of()));
        assertThatThrownBy(() ->
            productService.create(new CreateProductRequest("First", null, null, null, null, List.of()))
        ).isInstanceOf(ConflictException.class);
    }

    @Test
    void softDeleteProduct_excludedFromListQueries() {
        var resp = productService.create(new CreateProductRequest("Gone", null, null, null, null, List.of()));
        productService.softDelete(resp.id());
        assertThat(productRepo.findByIdAndDeletedAtIsNull(resp.id())).isEmpty();
    }

    @Test
    void changeStatus_updatesProductStatus() {
        var resp = productService.create(new CreateProductRequest("T-Shirt", null, null, null, null, List.of()));
        productService.changeStatus(resp.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        var updated = productService.getAdmin(resp.id());
        assertThat(updated.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void storefrontList_populatesFeaturedImageUrl() {
        BlobObject blob = new BlobObject();
        blob.setKey("products/test-product.jpg");
        blob.setFilename("test-product.jpg");
        blob.setContentType("image/jpeg");
        blob.setSize(50000L);
        var blobId = blobRepo.save(blob).getId();

        var product = productService.create(new CreateProductRequest("Image Product", null, null, null, null, List.of()));
        imageService.addImage(product.id(), new CreateImageRequest(blobId, "alt text"));
        productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));

        var result = productService.listStorefront(null, PageRequest.of(0, 100));
        var match = result.getContent().stream()
                .filter(p -> p.id().equals(product.id()))
                .findFirst().orElseThrow();
        assertThat(match.featuredImageUrl()).isNotNull();
        assertThat(match.featuredImageUrl()).contains("test-product.jpg");
    }

    @Test
    void storefrontList_returnsOnlyActiveProducts() {
        productService.create(new CreateProductRequest("Draft Product", null, null, null, null, List.of()));
        var active = productService.create(new CreateProductRequest("Active Product", null, null, null, null, List.of()));
        productService.changeStatus(active.id(), new ProductStatusRequest(ProductStatus.ACTIVE));

        var result = productService.listStorefront(null, PageRequest.of(0, 100));
        assertThat(result.getContent()).anyMatch(p -> p.title().equals("Active Product"));
        assertThat(result.getContent()).noneMatch(p -> p.title().equals("Draft Product"));
    }

    @Test
    void createVariant_autoCreatesInventoryItem() {
        var product = productService.create(new CreateProductRequest("Widget", null, null, null, null, List.of()));
        var req = new CreateVariantRequest(new java.math.BigDecimal("9.99"), null, null, null, null, null, List.of());
        var variant = variantService.create(product.id(), req);

        assertThat(variant.title()).isEqualTo("Default Title");
        assertThat(inventoryRepo.findByVariantId(variant.id())).isPresent();
        assertThat(inventoryRepo.findByVariantId(variant.id()).get().isRequiresShipping()).isTrue();
    }

    @Test
    void createVariant_withOptionValues_generatesTitle() {
        var product = productService.create(new CreateProductRequest("Shirt", null, null, null, null, List.of()));
        var colorOpt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
        var redVal = optionService.createOptionValue(product.id(), colorOpt.id(), new CreateOptionValueRequest("Red", 1));
        var sizeOpt = optionService.createOption(product.id(), new CreateOptionRequest("Size", 2));
        var lgVal = optionService.createOptionValue(product.id(), sizeOpt.id(), new CreateOptionValueRequest("Large", 1));

        var req = new CreateVariantRequest(new java.math.BigDecimal("19.99"), null, null, null, null, null,
            List.of(redVal.id(), lgVal.id()));
        var variant = variantService.create(product.id(), req);

        assertThat(variant.title()).isEqualTo("Red / Large");
    }

    @Test
    void renameOptionValue_recomputesVariantTitles() {
        var product = productService.create(new CreateProductRequest("Hat", null, null, null, null, List.of()));
        var opt = optionService.createOption(product.id(), new CreateOptionRequest("Color", 1));
        var val = optionService.createOptionValue(product.id(), opt.id(), new CreateOptionValueRequest("Blu", 1)); // typo

        var req = new CreateVariantRequest(new java.math.BigDecimal("25.00"), null, null, null, null, null,
            List.of(val.id()));
        variantService.create(product.id(), req);

        optionService.renameOptionValue(opt.id(), val.id(), new RenameOptionValueRequest("Blue"));

        var updated = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(product.id());
        assertThat(updated.get(0).getTitle()).isEqualTo("Blue");
    }

    @Test
    void softDeleteVariant_excludedFromProductVariants() {
        var product = productService.create(new CreateProductRequest("Cap", null, null, null, null, List.of()));
        var req = new CreateVariantRequest(new java.math.BigDecimal("15.00"), null, null, null, null, null, List.of());
        var variant = variantService.create(product.id(), req);
        variantService.softDelete(product.id(), variant.id());

        var variants = variantRepo.findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(product.id());
        assertThat(variants).isEmpty();
    }
}
