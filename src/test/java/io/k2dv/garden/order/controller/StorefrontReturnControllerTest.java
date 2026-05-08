package io.k2dv.garden.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.order.dto.ReturnRequestResponse;
import io.k2dv.garden.order.dto.SubmitReturnRequest;
import io.k2dv.garden.order.model.ReturnReason;
import io.k2dv.garden.order.model.ReturnRequestStatus;
import io.k2dv.garden.order.model.ReturnResolution;
import io.k2dv.garden.order.service.ReturnRequestService;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorefrontReturnController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class, TestCurrentUserConfig.class})
class StorefrontReturnControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean ReturnRequestService returnService;

    private ReturnRequestResponse stub(UUID id, UUID orderId) {
        return new ReturnRequestResponse(id, orderId, STUB_USER_ID,
            ReturnReason.DAMAGED, "broken", ReturnResolution.REFUND,
            ReturnRequestStatus.PENDING, null, null, null, List.of(),
            Instant.now(), Instant.now());
    }

    @Test
    void submit_paidOrder_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        when(returnService.submit(eq(orderId), eq(STUB_USER_ID), any()))
            .thenReturn(stub(returnId, orderId));

        mvc.perform(post("/api/v1/storefront/returns/orders/{orderId}", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new SubmitReturnRequest(ReturnReason.DAMAGED, "broken", ReturnResolution.REFUND, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(returnId.toString()))
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void submit_invalidOrderStatus_returns409() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(returnService.submit(eq(orderId), any(), any()))
            .thenThrow(new ConflictException("INVALID_ORDER_STATUS", "Returns can only be submitted for paid orders"));

        mvc.perform(post("/api/v1/storefront/returns/orders/{orderId}", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new SubmitReturnRequest(ReturnReason.CHANGED_MIND, null, null, null))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATUS"));
    }

    @Test
    void list_returns200WithPage() throws Exception {
        UUID returnId = UUID.randomUUID();
        PagedResult<ReturnRequestResponse> page = new PagedResult<>(
            List.of(stub(returnId, UUID.randomUUID())),
            PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(returnService.listForUser(eq(STUB_USER_ID), any())).thenReturn(page);

        mvc.perform(get("/api/v1/storefront/returns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content[0].id").value(returnId.toString()));
    }

    @Test
    void get_ownReturn_returns200() throws Exception {
        UUID returnId = UUID.randomUUID();
        when(returnService.getByIdForUser(eq(returnId), eq(STUB_USER_ID)))
            .thenReturn(stub(returnId, UUID.randomUUID()));

        mvc.perform(get("/api/v1/storefront/returns/{id}", returnId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(returnId.toString()));
    }

    @Test
    void get_otherUsersReturn_returns404() throws Exception {
        UUID returnId = UUID.randomUUID();
        when(returnService.getByIdForUser(eq(returnId), any()))
            .thenThrow(new NotFoundException("RETURN_NOT_FOUND", "Return request not found"));

        mvc.perform(get("/api/v1/storefront/returns/{id}", returnId))
            .andExpect(status().isNotFound());
    }
}
