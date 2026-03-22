package io.k2dv.garden.auth.service;

import io.k2dv.garden.auth.dto.*;
import io.k2dv.garden.auth.model.IdentityProvider;
import io.k2dv.garden.auth.repository.IdentityRepository;
import io.k2dv.garden.auth.repository.TokenRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.UnauthorizedException;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

class AuthServiceIT extends AbstractIntegrationTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired IdentityRepository identityRepo;
    @Autowired TokenRepository tokenRepo;
    @MockitoBean EmailService emailService;

    @Test
    void register_createsUserIdentityAndReturnsTokens() {
        var req = new RegisterRequest("alice@example.com", "password123", "Alice", "Smith");
        var resp = authService.register(req);

        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();

        var user = userRepo.findByEmail("alice@example.com").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.UNVERIFIED);

        var identity = identityRepo.findByUserIdAndProvider(user.getId(), IdentityProvider.CREDENTIALS);
        assertThat(identity).isPresent();
        assertThat(identity.get().getPasswordHash()).startsWith("$2a$"); // bcrypt

        verify(emailService).sendEmailVerification(anyString(), anyString());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        var req = new RegisterRequest("dup@example.com", "password123", "Dup", "User");
        authService.register(req);

        assertThatThrownBy(() -> authService.register(req))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void login_validCredentials_returnsTokens() {
        authService.register(new RegisterRequest("bob@example.com", "pass1234", "Bob", "Jones"));

        var resp = authService.login(new LoginRequest("bob@example.com", "pass1234"));

        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        authService.register(new RegisterRequest("carol@example.com", "correct1", "Carol", "C"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("carol@example.com", "wrong")))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refresh_validToken_returnsNewTokens() {
        authService.register(new RegisterRequest("dave@example.com", "pass1234", "Dave", "D"));
        var first = authService.login(new LoginRequest("dave@example.com", "pass1234"));

        var refreshed = authService.refresh(new RefreshRequest(first.refreshToken()));

        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();
        // Old refresh token must be consumed
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(first.refreshToken())))
            .isInstanceOf(UnauthorizedException.class);
    }
}
