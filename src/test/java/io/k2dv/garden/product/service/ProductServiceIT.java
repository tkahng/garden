package io.k2dv.garden.product.service;

import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductServiceIT extends AbstractIntegrationTest {

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepo;

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
    void storefrontList_returnsOnlyActiveProducts() {
        productService.create(new CreateProductRequest("Draft Product", null, null, null, null, List.of()));
        var active = productService.create(new CreateProductRequest("Active Product", null, null, null, null, List.of()));
        productService.changeStatus(active.id(), new ProductStatusRequest(ProductStatus.ACTIVE));

        var result = productService.listStorefront(null);
        @SuppressWarnings("unchecked")
        var items = (java.util.List<?>) result.get("items");
        assertThat(items).hasSize(1);
        assertThat(((io.k2dv.garden.product.dto.ProductSummaryResponse) items.get(0)).title())
            .isEqualTo("Active Product");
    }
}
