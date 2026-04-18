package io.k2dv.garden.b2b.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.service.PriceListService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminPriceListController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminPriceListControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean PriceListService priceListService;

    private PriceListResponse stubList(UUID id, UUID companyId) {
        return new PriceListResponse(id, companyId, "Contract", "USD", 0,
            null, null, Instant.now(), Instant.now());
    }

    private PriceListEntryResponse stubEntry(UUID listId, UUID variantId) {
        return new PriceListEntryResponse(UUID.randomUUID(), listId, variantId,
            new BigDecimal("75.00"), 1, Instant.now(), Instant.now());
    }

    @Test
    void createPriceList_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        when(priceListService.create(any())).thenReturn(stubList(listId, companyId));

        mvc.perform(post("/api/v1/admin/price-lists")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreatePriceListRequest(companyId, "Contract", "USD", 0, null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(listId.toString()))
            .andExpect(jsonPath("$.data.name").value("Contract"));
    }

    @Test
    void listByCompany_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(priceListService.listByCompany(eq(companyId)))
            .thenReturn(List.of(stubList(UUID.randomUUID(), companyId)));

        mvc.perform(get("/api/v1/admin/price-lists").param("companyId", companyId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getPriceList_returns200() throws Exception {
        UUID listId = UUID.randomUUID();
        when(priceListService.getById(eq(listId))).thenReturn(stubList(listId, UUID.randomUUID()));

        mvc.perform(get("/api/v1/admin/price-lists/{id}", listId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(listId.toString()));
    }

    @Test
    void getPriceList_notFound_returns404() throws Exception {
        when(priceListService.getById(any()))
            .thenThrow(new NotFoundException("PRICE_LIST_NOT_FOUND", "Not found"));

        mvc.perform(get("/api/v1/admin/price-lists/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void updatePriceList_returns200() throws Exception {
        UUID listId = UUID.randomUUID();
        when(priceListService.update(eq(listId), any())).thenReturn(stubList(listId, UUID.randomUUID()));

        mvc.perform(put("/api/v1/admin/price-lists/{id}", listId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdatePriceListRequest("Updated", "EUR", 5, null, null))))
            .andExpect(status().isOk());
    }

    @Test
    void deletePriceList_returns204() throws Exception {
        doNothing().when(priceListService).delete(any());

        mvc.perform(delete("/api/v1/admin/price-lists/{id}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void listEntries_returns200() throws Exception {
        UUID listId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(priceListService.listEntries(eq(listId))).thenReturn(List.of(stubEntry(listId, variantId)));

        mvc.perform(get("/api/v1/admin/price-lists/{id}/entries", listId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void upsertEntry_returns200() throws Exception {
        UUID listId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(priceListService.upsertEntry(eq(listId), eq(variantId), any()))
            .thenReturn(stubEntry(listId, variantId));

        mvc.perform(put("/api/v1/admin/price-lists/{id}/entries/{variantId}", listId, variantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpsertPriceListEntryRequest(new BigDecimal("75.00"), 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.price").value(75.00));
    }

    @Test
    void deleteEntry_returns204() throws Exception {
        doNothing().when(priceListService).deleteEntry(any(), any());

        mvc.perform(delete("/api/v1/admin/price-lists/{id}/entries/{variantId}",
                UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
