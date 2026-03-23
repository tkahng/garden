package io.k2dv.garden.product.controller;

import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.product.dto.ProductDetailResponse;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.shared.dto.CursorMeta;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorefrontProductController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class StorefrontProductControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProductService productService;

    @Test
    void listProducts_returns200WithCursorMeta() throws Exception {
        var items = List.of(new ProductSummaryResponse(UUID.randomUUID(), "Shirt", "shirt", null));
        var meta = CursorMeta.builder().nextCursor(null).hasMore(false).pageSize(20).build();
        when(productService.listStorefront(any())).thenReturn(Map.of("items", items, "meta", meta));

        mvc.perform(get("/api/v1/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].title").value("Shirt"));
    }

    @Test
    void getByHandle_activeProduct_returns200() throws Exception {
        var detail = new ProductDetailResponse(UUID.randomUUID(), "Shirt", null, "shirt",
            null, null, List.of(), List.of(), List.of());
        when(productService.getByHandle("shirt")).thenReturn(detail);

        mvc.perform(get("/api/v1/products/shirt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.handle").value("shirt"));
    }

    @Test
    void getByHandle_draftProduct_returns404() throws Exception {
        when(productService.getByHandle(any()))
            .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        mvc.perform(get("/api/v1/products/draft-product"))
            .andExpect(status().isNotFound());
    }
}
