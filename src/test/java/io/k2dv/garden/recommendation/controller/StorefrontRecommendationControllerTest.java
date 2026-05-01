package io.k2dv.garden.recommendation.controller;

import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.recommendation.service.RecommendationService;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorefrontRecommendationController.class)
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
class StorefrontRecommendationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RecommendationService recommendationService;

    private ProductSummaryResponse stubProduct() {
        return new ProductSummaryResponse(UUID.randomUUID(), "Rose Bush", "rose-bush",
                null, null, new BigDecimal("19.99"), new BigDecimal("19.99"), null, null);
    }

    @Test
    void getRelated_returns200WithList() throws Exception {
        when(recommendationService.findRelated(any(), anyInt()))
                .thenReturn(List.of(stubProduct(), stubProduct()));

        mvc.perform(get("/api/v1/products/{handle}/related", "rose-bush"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getRelated_productNotFound_returns404() throws Exception {
        when(recommendationService.findRelated(any(), anyInt()))
                .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Not found"));

        mvc.perform(get("/api/v1/products/{handle}/related", "no-such-product"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void getRelated_emptyResults_returns200() throws Exception {
        when(recommendationService.findRelated(any(), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/v1/products/{handle}/related", "lone-product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
