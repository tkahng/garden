package io.k2dv.garden.content.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.PageStatus;
import io.k2dv.garden.content.service.PageService;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminPageController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminPageControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean PageService pageService;

    private AdminPageResponse stubPage() {
        return new AdminPageResponse(UUID.randomUUID(), "About Us", "about-us", null,
            PageStatus.DRAFT, null, null, null, null, null, null);
    }

    @Test
    void createPage_returns201() throws Exception {
        when(pageService.create(any())).thenReturn(stubPage());

        mvc.perform(post("/api/v1/admin/pages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreatePageRequest("About Us", null, null, null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("About Us"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createPage_blankTitle_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/pages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getPage_notFound_returns404() throws Exception {
        when(pageService.get(any())).thenThrow(new NotFoundException("PAGE_NOT_FOUND", "Page not found"));

        mvc.perform(get("/api/v1/admin/pages/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("PAGE_NOT_FOUND"));
    }

    @Test
    void changeStatus_returns200() throws Exception {
        when(pageService.changeStatus(any(), any())).thenReturn(stubPage());

        mvc.perform(patch("/api/v1/admin/pages/{id}/status", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void deletePage_returns204() throws Exception {
        doNothing().when(pageService).delete(any());

        mvc.perform(delete("/api/v1/admin/pages/{id}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void listPages_withStatusFilter_returns200() throws Exception {
        var result = new PagedResult<>(List.of(stubPage()),
            PageMeta.builder().page(0).pageSize(10).total(1L).build());
        when(pageService.list(any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/admin/pages").param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }
}
