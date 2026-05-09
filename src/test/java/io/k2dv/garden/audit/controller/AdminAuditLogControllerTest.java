package io.k2dv.garden.audit.controller;

import io.k2dv.garden.audit.dto.AuditLogResponse;
import io.k2dv.garden.audit.service.AuditLogService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminAuditLogController.class)
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
class AdminAuditLogControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuditLogService auditLogService;

    private AuditLogResponse stub() {
        return new AuditLogResponse(UUID.randomUUID(), UUID.randomUUID(),
            "manager@test.com", "softDelete", "product",
            UUID.randomUUID().toString(), null, null, Instant.now());
    }

    private PagedResult<AuditLogResponse> stubPage(AuditLogResponse item) {
        return new PagedResult<>(List.of(item), PageMeta.builder().page(0).pageSize(25).total(1L).build());
    }

    @Test
    void list_returns200WithEntries() throws Exception {
        var item = stub();
        when(auditLogService.list(any(), any(), any(), any())).thenReturn(stubPage(item));

        mvc.perform(get("/api/v1/admin/audit-log"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].entityType").value("product"))
            .andExpect(jsonPath("$.data.content[0].action").value("softDelete"));
    }

    @Test
    void list_withEntityTypeFilter_passes200() throws Exception {
        when(auditLogService.list(eq("order"), any(), any(), any()))
            .thenReturn(stubPage(stub()));

        mvc.perform(get("/api/v1/admin/audit-log").param("entityType", "order"))
            .andExpect(status().isOk());
    }

    @Test
    void list_withActorEmailFilter_passes200() throws Exception {
        when(auditLogService.list(any(), any(), eq("manager@test.com"), any()))
            .thenReturn(stubPage(stub()));

        mvc.perform(get("/api/v1/admin/audit-log").param("actorEmail", "manager@test.com"))
            .andExpect(status().isOk());
    }

    @Test
    void list_emptyResult_returns200() throws Exception {
        when(auditLogService.list(any(), any(), any(), any()))
            .thenReturn(new PagedResult<>(List.of(), PageMeta.builder().page(0).pageSize(25).total(0L).build()));

        mvc.perform(get("/api/v1/admin/audit-log"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty());
    }
}
