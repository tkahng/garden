package io.k2dv.garden.webhook.controller;

import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.webhook.dto.WebhookDeliveryResponse;
import io.k2dv.garden.webhook.dto.WebhookEndpointResponse;
import io.k2dv.garden.webhook.model.WebhookDeliveryStatus;
import io.k2dv.garden.webhook.model.WebhookEventType;
import io.k2dv.garden.webhook.service.OutboundWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminWebhookController.class)
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
class AdminWebhookControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    OutboundWebhookService webhookService;

    private WebhookEndpointResponse stubEndpoint(UUID id) {
        return new WebhookEndpointResponse(id, "https://example.com/hook",
                "desc", List.of("ORDER_PLACED"), true, Instant.now(), Instant.now());
    }

    private WebhookDeliveryResponse stubDelivery(UUID endpointId) {
        return new WebhookDeliveryResponse(UUID.randomUUID(), endpointId,
                WebhookEventType.ORDER_PLACED, Map.of("orderId", UUID.randomUUID().toString()),
                WebhookDeliveryStatus.PENDING, 0, null, null, null, null, Instant.now());
    }

    @Test
    void listEventTypes_returns200() throws Exception {
        mvc.perform(get("/api/v1/admin/webhooks/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void create_validRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(webhookService.create(any())).thenReturn(stubEndpoint(id));

        mvc.perform(post("/api/v1/admin/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/hook\",\"secret\":\"s3cr3t\","
                                + "\"description\":\"test\",\"events\":[\"ORDER_PLACED\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void create_invalidUrl_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"not-a-url\",\"secret\":\"s3cr3t\",\"events\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankSecret_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"secret\":\"\",\"events\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_returns200() throws Exception {
        when(webhookService.list()).thenReturn(List.of(stubEndpoint(UUID.randomUUID())));

        mvc.perform(get("/api/v1/admin/webhooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getById_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(webhookService.getById(id)).thenReturn(stubEndpoint(id));

        mvc.perform(get("/api/v1/admin/webhooks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(webhookService.getById(any()))
                .thenThrow(new NotFoundException("WEBHOOK_ENDPOINT_NOT_FOUND", "Not found"));

        mvc.perform(get("/api/v1/admin/webhooks/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WEBHOOK_ENDPOINT_NOT_FOUND"));
    }

    @Test
    void update_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(webhookService.update(eq(id), any())).thenReturn(stubEndpoint(id));

        mvc.perform(put("/api/v1/admin/webhooks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete("/api/v1/admin/webhooks/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new NotFoundException("WEBHOOK_ENDPOINT_NOT_FOUND", "Not found"))
                .when(webhookService).delete(any());

        mvc.perform(delete("/api/v1/admin/webhooks/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WEBHOOK_ENDPOINT_NOT_FOUND"));
    }

    @Test
    void listDeliveries_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        var result = new PagedResult<>(
                List.of(stubDelivery(id)),
                PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(webhookService.listDeliveries(eq(id), any())).thenReturn(result);

        mvc.perform(get("/api/v1/admin/webhooks/{id}/deliveries", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }
}
