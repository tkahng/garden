package io.k2dv.garden.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.inventory.dto.*;
import io.k2dv.garden.inventory.model.*;
import io.k2dv.garden.inventory.service.InventoryService;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminInventoryController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminInventoryControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean InventoryService inventoryService;

    private InventoryLevelResponse stubLevel() {
        return new InventoryLevelResponse(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "Warehouse", 10, 0);
    }

    @Test
    void receiveStock_validRequest_returns200() throws Exception {
        when(inventoryService.receiveStock(any(), any())).thenReturn(stubLevel());

        mvc.perform(post("/api/v1/admin/inventory/variants/{variantId}/receive", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new ReceiveStockRequest(UUID.randomUUID(), 10, "Initial"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.quantityOnHand").value(10));
    }

    @Test
    void adjustStock_validRequest_returns200() throws Exception {
        when(inventoryService.adjustStock(any(), any())).thenReturn(stubLevel());

        mvc.perform(post("/api/v1/admin/inventory/variants/{variantId}/adjust", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AdjustStockRequest(UUID.randomUUID(), -2, InventoryTransactionReason.DAMAGED, null))))
            .andExpect(status().isOk());
    }

    @Test
    void adjustStock_invalidReason_returns400() throws Exception {
        when(inventoryService.adjustStock(any(), any()))
            .thenThrow(new ValidationException(
                "INVALID_REASON", "Use the receive endpoint for RECEIVED; SOLD is system-managed"));

        mvc.perform(post("/api/v1/admin/inventory/variants/{variantId}/adjust", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AdjustStockRequest(UUID.randomUUID(), 5, InventoryTransactionReason.RECEIVED, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_REASON"));
    }

    @Test
    void getLevels_returns200() throws Exception {
        when(inventoryService.getLevels(any())).thenReturn(List.of(stubLevel()));

        mvc.perform(get("/api/v1/admin/inventory/variants/{variantId}/levels", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listTransactions_returns200WithPage() throws Exception {
        var result = new PagedResult<>(List.<InventoryTransactionResponse>of(),
            PageMeta.builder().page(0).pageSize(20).total(0L).build());
        when(inventoryService.listTransactions(any(), any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/admin/inventory/variants/{variantId}/transactions", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void updateFulfillment_validRequest_returns200() throws Exception {
        var variantResp = new AdminVariantResponse(UUID.randomUUID(), "Default Title", null, null,
            BigDecimal.TEN, null, null, null, List.of(),
            FulfillmentType.MADE_TO_ORDER, InventoryPolicy.CONTINUE, 14, null);
        when(inventoryService.updateVariantFulfillment(any(), any())).thenReturn(variantResp);

        mvc.perform(patch("/api/v1/admin/inventory/variants/{variantId}/fulfillment", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new UpdateVariantFulfillmentRequest(FulfillmentType.MADE_TO_ORDER, InventoryPolicy.CONTINUE, 14))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fulfillmentType").value("MADE_TO_ORDER"))
            .andExpect(jsonPath("$.data.leadTimeDays").value(14));
    }
}
