package io.k2dv.garden.search.controller;

import io.k2dv.garden.collection.dto.response.CollectionSummaryResponse;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.search.dto.SearchArticleResult;
import io.k2dv.garden.search.dto.SearchPageResult;
import io.k2dv.garden.search.dto.SearchResponse;
import io.k2dv.garden.search.service.SearchService;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SearchController.class)
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
class SearchControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SearchService searchService;

    private SearchResponse emptySearch() {
        PageMeta meta = PageMeta.builder().page(0).pageSize(10).total(0L).build();
        return new SearchResponse(
                new PagedResult<>(List.of(), meta),
                new PagedResult<>(List.of(), meta),
                new PagedResult<>(List.of(), meta),
                new PagedResult<>(List.of(), meta));
    }

    private SearchResponse searchWithProduct() {
        PageMeta meta = PageMeta.builder().page(0).pageSize(10).total(1L).build();
        ProductSummaryResponse product = new ProductSummaryResponse(
                UUID.randomUUID(), "Rose Bush", "rose-bush", null, null, null, null, null, null);
        return new SearchResponse(
                new PagedResult<>(List.of(product), meta),
                new PagedResult<>(List.of(), meta),
                new PagedResult<>(List.of(), meta),
                new PagedResult<>(List.of(), meta));
    }

    @Test
    void search_validQuery_returns200() throws Exception {
        when(searchService.search(any(), any(), any())).thenReturn(searchWithProduct());

        mvc.perform(get("/api/v1/search").param("q", "rose"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products.content").isArray())
                .andExpect(jsonPath("$.data.products.content.length()").value(1));
    }

    @Test
    void search_blankQuery_returns400() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("QUERY_REQUIRED"));
    }

    @Test
    void search_withTypeFilter_returns200() throws Exception {
        when(searchService.search(any(), any(), any())).thenReturn(emptySearch());

        mvc.perform(get("/api/v1/search")
                        .param("q", "garden")
                        .param("types", "products"))
                .andExpect(status().isOk());
    }

    @Test
    void search_limitClampedAt50_returns200() throws Exception {
        when(searchService.search(any(), any(), any())).thenReturn(emptySearch());

        mvc.perform(get("/api/v1/search")
                        .param("q", "garden")
                        .param("limit", "100"))
                .andExpect(status().isOk());
    }
}
