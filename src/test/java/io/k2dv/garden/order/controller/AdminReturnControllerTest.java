package io.k2dv.garden.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.order.dto.ReturnRequestResponse;
import io.k2dv.garden.order.dto.ReviewReturnRequest;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminReturnController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class, TestCurrentUserConfig.class})
class AdminReturnControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean ReturnRequestService returnService;

    private ReturnRequestResponse stub(UUID id, ReturnRequestStatus status) {
        return new ReturnRequestResponse(id, UUID.randomUUID(), UUID.randomUUID(),
            ReturnReason.DAMAGED, "broken", ReturnResolution.REFUND,
            status, null, null, null, List.of(),
            Instant.now(), Instant.now());
    }

    @Test
    void list_noFilter_returns200() throws Exception {
        UUID returnId = UUID.randomUUID();
        PagedResult<ReturnRequestResponse> page = new PagedResult<>(
            List.of(stub(returnId, ReturnRequestStatus.PENDING)),
            PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(returnService.listAll(isNull(), any())).thenReturn(page);

        mvc.perform(get("/api/v1/admin/returns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void list_withStatusFilter_returns200() throws Exception {
        PagedResult<ReturnRequestResponse> page = new PagedResult<>(
            List.of(stub(UUID.randomUUID(), ReturnRequestStatus.PENDING)),
            PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(returnService.listAll(eq(ReturnRequestStatus.PENDING), any())).thenReturn(page);

        mvc.perform(get("/api/v1/admin/returns").param("status", "PENDING"))
            .andExpect(status().isOk());
    }

    @Test
    void get_returns200() throws Exception {
        UUID returnId = UUID.randomUUID();
        when(returnService.getById(eq(returnId))).thenReturn(stub(returnId, ReturnRequestStatus.PENDING));

        mvc.perform(get("/api/v1/admin/returns/{id}", returnId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(returnId.toString()));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(returnService.getById(any()))
            .thenThrow(new NotFoundException("RETURN_NOT_FOUND", "Not found"));

        mvc.perform(get("/api/v1/admin/returns/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void approve_returns200() throws Exception {
        UUID returnId = UUID.randomUUID();
        when(returnService.approve(eq(returnId), eq(STUB_USER_ID), any()))
            .thenReturn(stub(returnId, ReturnRequestStatus.APPROVED));

        mvc.perform(post("/api/v1/admin/returns/{id}/approve", returnId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReviewReturnRequest("All good"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void approve_notPending_returns409() throws Exception {
        UUID returnId = UUID.randomUUID();
        when(returnService.approve(eq(returnId), any(), any()))
            .thenThrow(new ConflictException("INVALID_RETURN_STATUS", "Only PENDING returns can be approved"));

        mvc.perform(post("/api/v1/admin/returns/{id}/approve", returnId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("INVALID_RETURN_STATUS"));
    }

    @Test
    void reject_returns200() throws Exception {
        UUID returnId = UUID.randomUUID();
        when(returnService.reject(eq(returnId), eq(STUB_USER_ID), any()))
            .thenReturn(stub(returnId, ReturnRequestStatus.REJECTED));

        mvc.perform(post("/api/v1/admin/returns/{id}/reject", returnId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReviewReturnRequest("Not eligible"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void complete_returns200() throws Exception {
        UUID returnId = UUID.randomUUID();
        when(returnService.complete(eq(returnId), eq(STUB_USER_ID)))
            .thenReturn(stub(returnId, ReturnRequestStatus.COMPLETED));

        mvc.perform(post("/api/v1/admin/returns/{id}/complete", returnId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }
}
