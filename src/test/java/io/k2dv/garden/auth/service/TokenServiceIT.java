package io.k2dv.garden.auth.service;

import io.k2dv.garden.auth.model.TokenType;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.UnauthorizedException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceIT extends AbstractIntegrationTest {

    @Autowired TokenService tokenService;
    @Autowired UserRepository userRepo;

    private User savedUser() {
        var u = new User();
        u.setEmail("tok@test.com");
        u.setFirstName("T");
        u.setLastName("U");
        return userRepo.saveAndFlush(u);
    }

    @Test
    void createAndValidate_roundTrip() {
        var user = savedUser();
        String raw = tokenService.createToken(user.getId(), TokenType.REFRESH_TOKEN, Duration.ofDays(30));
        assertThat(raw).isNotBlank();

        var userId = tokenService.validateAndConsume(raw, TokenType.REFRESH_TOKEN);
        assertThat(userId).isEqualTo(user.getId());
    }

    @Test
    void validate_afterConsume_throwsUnauthorized() {
        var user = savedUser();
        String raw = tokenService.createToken(user.getId(), TokenType.EMAIL_VERIFICATION, Duration.ofHours(24));
        tokenService.validateAndConsume(raw, TokenType.EMAIL_VERIFICATION);

        assertThatThrownBy(() -> tokenService.validateAndConsume(raw, TokenType.EMAIL_VERIFICATION))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void validate_wrongType_throwsUnauthorized() {
        var user = savedUser();
        String raw = tokenService.createToken(user.getId(), TokenType.REFRESH_TOKEN, Duration.ofDays(30));

        assertThatThrownBy(() -> tokenService.validateAndConsume(raw, TokenType.EMAIL_VERIFICATION))
            .isInstanceOf(UnauthorizedException.class);
    }
}
