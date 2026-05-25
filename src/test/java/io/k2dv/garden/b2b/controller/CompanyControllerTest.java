package io.k2dv.garden.b2b.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.model.CompanyRole;
import io.k2dv.garden.b2b.service.CompanyInvitationService;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.b2b.service.CompanyShippingAddressService;
import io.k2dv.garden.b2b.service.InvoiceService;
import io.k2dv.garden.b2b.service.PriceListService;
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
    @MockitoBean
    CompanyInvitationService invitationService;
    @MockitoBean
    PriceListService priceListService;
    @MockitoBean
    InvoiceService invoiceService;
    @MockitoBean
    io.k2dv.garden.b2b.service.CreditAccountService creditAccountService;
    @MockitoBean
    CompanyShippingAddressService shippingAddressService;

    private CompanyResponse stubCompany(UUID id) {
        return new CompanyResponse(id, "Acme", null, null, null, null, null, null, null, null, false,
            null, null, Instant.now(), Instant.now());
    }

    private CompanyMemberResponse stubMember(UUID userId) {
        return new CompanyMemberResponse(UUID.randomUUID(), userId, "user@example.com",
            "Test", "User", CompanyRole.MEMBER, null, null, Instant.now());
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
                    new UpdateCompanyRequest("Updated", null, null, null, null, null, null, null, null, null))))
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
                .content(objectMapper.writeValueAsString(new AddMemberRequest("user@example.com", null))))
            .andExpect(status().isOk());
    }

    @Test
    void addMember_userNotFound_returns404() throws Exception {
        when(companyService.addMember(any(), any(), any()))
            .thenThrow(new NotFoundException("USER_NOT_FOUND", "Not found"));

        mvc.perform(post("/api/v1/companies/{id}/members", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest("nobody@example.com", null))))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateMemberRole_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(companyService.updateMemberRole(eq(companyId), eq(memberId), any(), any()))
            .thenReturn(stubMember(memberId));

        mvc.perform(put("/api/v1/companies/{id}/members/{userId}/role", companyId, memberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new io.k2dv.garden.b2b.dto.UpdateMemberRoleRequest(
                        io.k2dv.garden.b2b.model.CompanyRole.MANAGER))))
            .andExpect(status().isOk());
    }

    @Test
    void removeMember_returns204() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        doNothing().when(companyService).removeMember(any(), any(), any());

        mvc.perform(delete("/api/v1/companies/{id}/members/{userId}", companyId, memberId))
            .andExpect(status().isNoContent());
    }

    // ─── Shipping addresses ───────────────────────────────────────────────────

    private CompanyAddressResponse stubAddress(UUID companyId, UUID addressId, boolean isDefault) {
        return new CompanyAddressResponse(addressId, companyId, "HQ",
            "Jane", "Doe", "Acme", "123 Main St", null, "Springfield",
            "IL", "62701", "US", isDefault, Instant.now(), Instant.now());
    }

    private CompanyAddressRequest addressReq(boolean isDefault) {
        return new CompanyAddressRequest("HQ", "Jane", "Doe", "Acme",
            "123 Main St", null, "Springfield", "IL", "62701", "US", isDefault);
    }

    @Test
    void listAddresses_member_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        when(shippingAddressService.list(eq(companyId), any()))
            .thenReturn(List.of(stubAddress(companyId, addressId, true)));

        mvc.perform(get("/api/v1/companies/{id}/addresses", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].id").value(addressId.toString()))
            .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    void addAddress_ownerOrManager_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        when(shippingAddressService.add(eq(companyId), any(), any()))
            .thenReturn(stubAddress(companyId, addressId, false));

        mvc.perform(post("/api/v1/companies/{id}/addresses", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addressReq(false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(addressId.toString()));
    }

    @Test
    void addAddress_nonManager_returns403() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(shippingAddressService.add(eq(companyId), any(), any()))
            .thenThrow(new ForbiddenException("INSUFFICIENT_COMPANY_ROLE",
                "Only a company owner or manager can manage shipping addresses"));

        mvc.perform(post("/api/v1/companies/{id}/addresses", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addressReq(false))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("INSUFFICIENT_COMPANY_ROLE"));
    }

    @Test
    void updateAddress_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        when(shippingAddressService.update(eq(companyId), eq(addressId), any(), any()))
            .thenReturn(stubAddress(companyId, addressId, false));

        mvc.perform(put("/api/v1/companies/{id}/addresses/{addressId}", companyId, addressId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addressReq(false))))
            .andExpect(status().isOk());
    }

    @Test
    void deleteAddress_returns204() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        doNothing().when(shippingAddressService).delete(eq(companyId), eq(addressId), any());

        mvc.perform(delete("/api/v1/companies/{id}/addresses/{addressId}", companyId, addressId))
            .andExpect(status().isNoContent());
    }

    @Test
    void setDefaultAddress_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        when(shippingAddressService.setDefault(eq(companyId), eq(addressId), any()))
            .thenReturn(stubAddress(companyId, addressId, true));

        mvc.perform(put("/api/v1/companies/{id}/addresses/{addressId}/default", companyId, addressId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void setDefaultAddress_notFound_returns404() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        when(shippingAddressService.setDefault(eq(companyId), eq(addressId), any()))
            .thenThrow(new NotFoundException("ADDRESS_NOT_FOUND", "Shipping address not found"));

        mvc.perform(put("/api/v1/companies/{id}/addresses/{addressId}/default", companyId, addressId))
            .andExpect(status().isNotFound());
    }
}
