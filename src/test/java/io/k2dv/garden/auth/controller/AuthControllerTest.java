package io.k2dv.garden.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.auth.dto.AuthTokenResponse;
import io.k2dv.garden.auth.dto.LoginRequest;
import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    AuthService authService;
    @MockitoBean
    CartService cartService;

    @Test
    void register_validBody_returns200WithTokens() throws Exception {
        when(authService.register(any())).thenReturn(new AuthTokenResponse("acc.tok.en", "ref.tok.en"));

        RegisterRequest req = new RegisterRequest("user@example.com", "password123", "Jane", "Doe");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("acc.tok.en"))
                .andExpect(jsonPath("$.data.refreshToken").value("ref.tok.en"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest("not-an-email", "password123", "Jane", "Doe");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validBody_returns200WithTokens() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(new AuthTokenResponse("acc.tok.en", "ref.tok.en"));
        when(authService.resolveUserId(any())).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest("user@example.com", "password123");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("acc.tok.en"));
    }

    @Test
    void login_withGuestSessionHeader_passesSessionIdToService() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(authService.login(any(LoginRequest.class))).thenReturn(new AuthTokenResponse("acc.tok.en", "ref.tok.en"));
        when(authService.resolveUserId(any())).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest("user@example.com", "password123");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("X-Guest-Session", sessionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("acc.tok.en"));
    }
}
