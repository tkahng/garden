package io.k2dv.garden.b2b.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.model.CompanyRole;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.exception.ForbiddenException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CompanyController.class)
@Import({TestSecurityConfig.class, TestCurrentUserConfig.class, GlobalExceptionHandler.class})
class CompanyControllerTest {

    @Autowired
    MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean
    CompanyService companyService;

    private CompanyResponse stubCompany(UUID id) {
        return new CompanyResponse(id, "Acme", null, null, null, null, null, null, null, null,
            Instant.now(), Instant.now());
    }

    private CompanyMemberResponse stubMember(UUID userId) {
        return new CompanyMemberResponse(UUID.randomUUID(), userId, "user@example.com",
            "Test", "User", CompanyRole.MEMBER, Instant.now());
    }

    @Test
    void createCompany_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.create(any(), any())).thenReturn(stubCompany(id));

        mvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateCompanyRequest("Acme", null, null, null, null, null, null, null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void listCompanies_returns200() throws Exception {
        when(companyService.listForUser(any())).thenReturn(List.of(stubCompany(UUID.randomUUID())));

        mvc.perform(get("/api/v1/companies"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getCompany_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.getById(eq(id), any())).thenReturn(stubCompany(id));

        mvc.perform(get("/api/v1/companies/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Acme"));
    }

    @Test
    void getCompany_notMember_returns403() throws Exception {
        when(companyService.getById(any(), any()))
            .thenThrow(new ForbiddenException("NOT_A_MEMBER", "Not a member"));

        mvc.perform(get("/api/v1/companies/{id}", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateCompany_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.update(eq(id), any(), any())).thenReturn(stubCompany(id));

        mvc.perform(put("/api/v1/companies/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateCompanyRequest("Updated", null, null, null, null, null, null, null, null))))
            .andExpect(status().isOk());
    }

    @Test
    void listMembers_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(companyService.listMembers(eq(companyId), any()))
            .thenReturn(List.of(stubMember(UUID.randomUUID())));

        mvc.perform(get("/api/v1/companies/{id}/members", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void addMember_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(companyService.addMember(eq(companyId), any(), any())).thenReturn(stubMember(memberId));

        mvc.perform(post("/api/v1/companies/{id}/members", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest("user@example.com"))))
            .andExpect(status().isOk());
    }

    @Test
    void addMember_userNotFound_returns404() throws Exception {
        when(companyService.addMember(any(), any(), any()))
            .thenThrow(new NotFoundException("USER_NOT_FOUND", "Not found"));

        mvc.perform(post("/api/v1/companies/{id}/members", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest("nobody@example.com"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void removeMember_returns204() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        doNothing().when(companyService).removeMember(any(), any(), any());

        mvc.perform(delete("/api/v1/companies/{id}/members/{userId}", companyId, memberId))
            .andExpect(status().isNoContent());
    }
}
