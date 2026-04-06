package io.k2dv.garden.admin.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.admin.iam.dto.CreateRoleRequest;
import io.k2dv.garden.admin.iam.dto.PermissionResponse;
import io.k2dv.garden.admin.iam.dto.RoleResponse;
import io.k2dv.garden.admin.iam.service.AdminIamService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminIamController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminIamControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AdminIamService adminIamService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listRoles_returns200() throws Exception {
        when(adminIamService.listRoles()).thenReturn(List.of(
            new RoleResponse(UUID.randomUUID(), "CUSTOMER", "Storefront customer", List.of())));

        mvc.perform(get("/api/v1/admin/iam/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("CUSTOMER"));
    }

    @Test
    void createRole_validBody_returns200() throws Exception {
        var roleId = UUID.randomUUID();
        when(adminIamService.createRole(any()))
            .thenReturn(new RoleResponse(roleId, "ANALYST", "Analyst role", List.of()));

        mvc.perform(post("/api/v1/admin/iam/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateRoleRequest("ANALYST", "Analyst role"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("ANALYST"));
    }

    @Test
    void createRole_missingName_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/iam/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listPermissions_returns200() throws Exception {
        when(adminIamService.listPermissions()).thenReturn(List.of(
            new PermissionResponse(UUID.randomUUID(), "product:read", "product", "read")));

        mvc.perform(get("/api/v1/admin/iam/permissions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("product:read"));
    }
}
