package io.k2dv.garden.b2b.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.b2b.dto.CreditAccountResponse;
import io.k2dv.garden.b2b.dto.CreateCreditAccountRequest;
import io.k2dv.garden.b2b.dto.UpdateCreditAccountRequest;
import io.k2dv.garden.b2b.service.CreditAccountService;
import io.k2dv.garden.config.TestSecurityConfig;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminCreditAccountController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminCreditAccountControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean CreditAccountService creditAccountService;

    private CreditAccountResponse stubAccount(UUID id, UUID companyId) {
        return new CreditAccountResponse(id, companyId,
            new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("10000.00"),
            30, "USD", Instant.now(), Instant.now());
    }

    @Test
    void create_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(creditAccountService.create(any())).thenReturn(stubAccount(accountId, companyId));

        mvc.perform(post("/api/v1/admin/credit-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateCreditAccountRequest(companyId, new BigDecimal("10000.00"), null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(accountId.toString()))
            .andExpect(jsonPath("$.data.creditLimit").value(10000.00))
            .andExpect(jsonPath("$.data.paymentTermsDays").value(30));
    }

    @Test
    void create_duplicateCompany_returns409() throws Exception {
        when(creditAccountService.create(any()))
            .thenThrow(new ConflictException("CREDIT_ACCOUNT_EXISTS",
                "A credit account already exists for this company"));

        mvc.perform(post("/api/v1/admin/credit-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateCreditAccountRequest(UUID.randomUUID(), new BigDecimal("1000.00"), null, null))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("CREDIT_ACCOUNT_EXISTS"));
    }

    @Test
    void getByCompany_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(creditAccountService.getByCompany(eq(companyId))).thenReturn(stubAccount(accountId, companyId));

        mvc.perform(get("/api/v1/admin/credit-accounts/company/{companyId}", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.companyId").value(companyId.toString()));
    }

    @Test
    void getByCompany_notFound_returns404() throws Exception {
        when(creditAccountService.getByCompany(any()))
            .thenThrow(new NotFoundException("CREDIT_ACCOUNT_NOT_FOUND",
                "No credit account found for this company"));

        mvc.perform(get("/api/v1/admin/credit-accounts/company/{companyId}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("CREDIT_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void update_returns200() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(creditAccountService.update(eq(companyId), any()))
            .thenReturn(stubAccount(UUID.randomUUID(), companyId));

        mvc.perform(put("/api/v1/admin/credit-accounts/company/{companyId}", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateCreditAccountRequest(new BigDecimal("20000.00"), 45))))
            .andExpect(status().isOk());
    }

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(creditAccountService).delete(any());

        mvc.perform(delete("/api/v1/admin/credit-accounts/company/{companyId}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
