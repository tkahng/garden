package io.k2dv.garden.admin.user.controller;

import io.k2dv.garden.admin.user.dto.AdminUserResponse;
import io.k2dv.garden.admin.user.service.AdminUserService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.user.model.UserStatus;
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

@WebMvcTest(controllers = AdminUserController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminUserControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AdminUserService adminUserService;

    @Test
    void listUsers_returns200WithPage() throws Exception {
        var user = new AdminUserResponse(UUID.randomUUID(), "u@example.com", "U", "U",
            null, UserStatus.ACTIVE, null, null, List.of("CUSTOMER"));
        var paged = new PagedResult<>(List.of(user), PageMeta.builder().page(0).pageSize(20).total(1).build());
        when(adminUserService.listUsers(any(), any())).thenReturn(paged);

        mvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].email").value("u@example.com"))
            .andExpect(jsonPath("$.data.meta.total").value(1));
    }

    @Test
    void assignRole_invalidBody_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/users/{id}/roles", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void suspendUser_returns204() throws Exception {
        mvc.perform(put("/api/v1/admin/users/{id}/suspend", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
