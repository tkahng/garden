package io.k2dv.garden.product.controller;

import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.product.dto.ProductDetailResponse;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.b2b.service.PriceListService;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorefrontProductController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class StorefrontProductControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProductService productService;
    @MockitoBean PriceListService priceListService;
    @MockitoBean CompanyService companyService;

    @Test
    void listProducts_returns200WithPageMeta() throws Exception {
        var items = List.of(new ProductSummaryResponse(UUID.randomUUID(), "Shirt", "shirt", null, null, new java.math.BigDecimal("19.99"), new java.math.BigDecimal("19.99"), null, null));
        var meta = PageMeta.builder().page(0).pageSize(20).total(1L).build();
        when(productService.listStorefront(any(), any())).thenReturn(new PagedResult<>(items, meta));

        mvc.perform(get("/api/v1/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].title").value("Shirt"));
    }

    @Test
    void getByHandle_activeProduct_returns200() throws Exception {
        var detail = new ProductDetailResponse(UUID.randomUUID(), "Shirt", null, "shirt",
            null, null, List.of(), List.of(), List.of(), null, null, null);
        when(productService.getByHandle(any(), any())).thenReturn(detail);

        mvc.perform(get("/api/v1/products/shirt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.handle").value("shirt"));
    }

    @Test
    void getByHandle_draftProduct_returns404() throws Exception {
        when(productService.getByHandle(any(), any()))
            .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        mvc.perform(get("/api/v1/products/draft-product"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getByHandle_catalogRestricted_noCompanyId_returns404() throws Exception {
        when(productService.getByHandle(any(), isNull()))
            .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        mvc.perform(get("/api/v1/products/restricted-product"))
            .andExpect(status().isNotFound());
    }

    @Test
    void listProducts_unauthenticatedWithCompanyId_companyIdDropped() throws Exception {
        var meta = PageMeta.builder().page(0).pageSize(20).total(0L).build();
        // In WebMvc tests there is no JWT, so companyId is silently dropped to null
        when(productService.listStorefront(any(), any())).thenReturn(new PagedResult<>(List.of(), meta));

        mvc.perform(get("/api/v1/products").param("companyId", UUID.randomUUID().toString()))
            .andExpect(status().isOk());

        // Verify companyService.requireMemberAccess was never called (no JWT in test context)
        org.mockito.Mockito.verify(companyService, org.mockito.Mockito.never())
            .requireMemberAccess(any(), any());
    }
}
