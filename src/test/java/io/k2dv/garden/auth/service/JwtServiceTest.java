package io.k2dv.garden.auth.service;

import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    // 32-byte base64 test secret
    private static final String TEST_SECRET =
        "dGVzdFNlY3JldEtleVRoYXRJc0F0TGVhc3QzMkJ5dGVzTG9uZw==";

    JwtService jwtService;
    NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() {
        var props = new AppProperties();
        props.getJwt().setSecret(TEST_SECRET);
        props.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));
        jwtService = new JwtService(props);

        byte[] keyBytes = Base64.getDecoder().decode(TEST_SECRET);
        var key = new SecretKeySpec(keyBytes, "HmacSHA256");
        decoder = NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
            .build();
    }

    @Test
    void mintAccessToken_includesSubEmailPermissions() throws Exception {
        var user = new User();
        user.setEmail("alice@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        // Simulate UUIDv7 id
        var userId = UUID.fromString("01906a42-0000-7000-8000-000000000001");
        // Inject id via reflection (BaseEntity id is private)
        var idField = io.k2dv.garden.shared.model.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, userId);

        var token = jwtService.mintAccessToken(user, List.of("product:read", "content:read"));
        var jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("alice@example.com");
        assertThat(jwt.getClaimAsStringList("permissions"))
            .containsExactlyInAnyOrder("product:read", "content:read");
        assertThat(jwt.getClaimAsString("emailVerifiedAt")).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void mintAccessToken_unverifiedUser_omitsEmailVerifiedAt() throws Exception {
        var user = new User();
        user.setEmail("bob@example.com");
        user.setStatus(UserStatus.UNVERIFIED);
        var idField = io.k2dv.garden.shared.model.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, UUID.randomUUID());

        var token = jwtService.mintAccessToken(user, List.of());
        var jwt = decoder.decode(token);

        assertThat(jwt.getClaims()).doesNotContainKey("emailVerifiedAt");
    }
}
