package io.k2dv.garden.account.controller;

import io.k2dv.garden.account.dto.AccountResponse;
import io.k2dv.garden.account.service.AccountService;
import io.k2dv.garden.auth.security.CurrentUserArgumentResolver;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(controllers = AccountController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AccountControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AccountService accountService;
    @MockitoBean CurrentUserArgumentResolver currentUserArgumentResolver;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setupResolver() throws Exception {
        User mockUser = org.mockito.Mockito.mock(User.class);
        when(mockUser.getId()).thenReturn(userId);
        when(currentUserArgumentResolver.supportsParameter(
                org.mockito.ArgumentMatchers.argThat(p ->
                    p.hasParameterAnnotation(io.k2dv.garden.auth.security.CurrentUser.class)
                        && p.getParameterType().equals(User.class))))
            .thenReturn(true);
        when(currentUserArgumentResolver.resolveArgument(any(), any(), any(), any()))
            .thenReturn(mockUser);
    }

    @Test
    void getAccount_returns200() throws Exception {
        var resp = new AccountResponse(userId, "user@example.com", "Jane", "Doe", null, UserStatus.ACTIVE, null);
        when(accountService.getAccount(any())).thenReturn(resp);

        mvc.perform(get("/api/v1/account"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    @Test
    void listAddresses_returns200() throws Exception {
        when(accountService.listAddresses(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/account/addresses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createAddress_invalidBody_returns400() throws Exception {
        // missing required fields
        mvc.perform(post("/api/v1/account/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
}
