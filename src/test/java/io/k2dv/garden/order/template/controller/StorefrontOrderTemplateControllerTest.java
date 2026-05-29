package io.k2dv.garden.order.template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.order.template.dto.CreateOrderTemplateRequest;
import io.k2dv.garden.order.template.dto.OrderTemplateItemInput;
import io.k2dv.garden.order.template.dto.OrderTemplateResponse;
import io.k2dv.garden.order.template.service.OrderTemplateService;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.k2dv.garden.config.TestCurrentUserConfig.STUB_USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorefrontOrderTemplateController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class, TestCurrentUserConfig.class})
class StorefrontOrderTemplateControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean OrderTemplateService templateService;

    private OrderTemplateResponse stub(UUID id, String name) {
        return new OrderTemplateResponse(id, STUB_USER_ID, name,
            List.of(), Instant.now(), Instant.now());
    }

    @Test
    void list_returns200() throws Exception {
        when(templateService.listForUser(STUB_USER_ID))
            .thenReturn(List.of(stub(UUID.randomUUID(), "Monthly order")));

        mvc.perform(get("/api/v1/storefront/order-templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].name").value("Monthly order"));
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.create(eq(STUB_USER_ID), any())).thenReturn(stub(id, "Q1 Order"));

        mvc.perform(post("/api/v1/storefront/order-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrderTemplateRequest(
                    "Q1 Order",
                    List.of(new OrderTemplateItemInput(UUID.randomUUID(), 5))))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.name").value("Q1 Order"));
    }

    @Test
    void create_emptyName_returns400() throws Exception {
        mvc.perform(post("/api/v1/storefront/order-templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrderTemplateRequest(
                    "", List.of(new OrderTemplateItemInput(UUID.randomUUID(), 1))))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void get_ownedTemplate_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.getById(STUB_USER_ID, id)).thenReturn(stub(id, "My template"));

        mvc.perform(get("/api/v1/storefront/order-templates/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.getById(eq(STUB_USER_ID), eq(id)))
            .thenThrow(new NotFoundException("TEMPLATE_NOT_FOUND", "Order template not found"));

        mvc.perform(get("/api/v1/storefront/order-templates/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(templateService).delete(STUB_USER_ID, id);

        mvc.perform(delete("/api/v1/storefront/order-templates/{id}", id))
            .andExpect(status().isNoContent());
    }

    @Test
    void load_returns200WithCart() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.loadToCart(eq(STUB_USER_ID), eq(id)))
            .thenReturn(new CartResponse(UUID.randomUUID(),
                io.k2dv.garden.cart.model.CartStatus.ACTIVE, null, "USD", List.of(), null));

        mvc.perform(post("/api/v1/storefront/order-templates/{id}/load", id))
            .andExpect(status().isOk());
    }
}
