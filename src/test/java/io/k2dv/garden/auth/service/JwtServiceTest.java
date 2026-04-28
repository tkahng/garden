package io.k2dv.garden.auth.service;

import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    // Dev RSA-2048 key pair (same keys used in test properties)
    private static final String TEST_PRIVATE_KEY =
        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDKSiNYKc50/HJUgYBH8z4yB1y/aRZQOMPFDeUHOw+mOXKzlh8HcesLVusH+tsNkoofv0uyKF75cwhYKDSgZpXWmNT8OKnV8+PZQyWJfhOIhOG5rK8NqmJ4wOfsum/f3HduPMFCdaWkkhWaiRScl7bHLd/6lFwVvFRnBjI6iJlaQPemHb2XYZMc0VQanExbtx54sTR4k8yd0dfL5ylnb4ndkILo/w9ge5+RnknCEp/5JOxAviB1m+bbvEElmMD3ELRISdqhLpvMwd9Izb1010+G0FkXMg0YtBBx3yfxE1jOWwIeQwyK5tlH4ZpGGQ5VLrj2scRA7aJYVrj50tOXOwXPAgMBAAECggEASZ46h6rLRnra9tMcRtMIpvdT8xsA8lf+MxgP6hY95z4P9rhi+XglVH6g0UojbyiN2Ojq2N7lVX3eIwsav+clj7AWDuZmNIVqPda4cfWukSfe6A4mtN/1r0FxBg+BiGQ5GKbGpHOhQSei8hcCJ8z0yT7yPMTCUGy2ALpaDEBLIvNM7WZa3aCQwbDXAN0981jF7zEAFPS22TFFmkasCMPdGf9uNI4Gpq283HGmRsZBqgQJHYDySTngg/kIjfXULJ82PMt00aTm3P75iconD9KyQ2gQ3drKJ+E+pJaJ8RX4wjyEhxwLMuVEzpepiQjjcOxWouZ7FXv25+yd9CBGKY3tEQKBgQDwGjFHztz/Mj0Rc1LwnJnKEvlp3IVWlsulEWLsGZfhVGsRNU+iZXrs5bQsCO2WEZyqdekQjdzBKrfOniLBMlDF5G6pMsm1QB3XwvlbFssUoi+qWpLAYcrwr9cqsqxtWNcPI57zfauJfVbPSzEELrO9mOLl0t1viLO7vc6KmrSk/wKBgQDXrwIv8L4Ve70Dc8dydo2ziioRgUitKqRivpekcXZ0+o0O3iOlaOjtmL6L3brERepWrIlGYwT82h2TA6TLu6IAlVSRDL5WbnA2gYX/tz2ZFYPuDKOXzI+tC48tfVgoT3/qdezwCL7T0gd0wWx/O9DOeexdYZBFqQylX17bXIOPMQKBgQC5OKt9psJrh9j+ZkW8YkJtVdfcZ4bhNnEhBNmYI2I8LMBvxP2K3NhIeidUtQp7c1i1U0KZR6bdyVOEbA+JlIQlNUR8+DYMwcqD0OBF+b5uS3OT6k09ZfOFW1EUmkNUE23MOwF6+x5icTOPtIS6okB8ab3X56R1TzLACPuVhyUGBwKBgEK4B3QvA43/vgOAYDUfSUw9tC/AD2xJ4ZoOHTioRJ2BF/t+agZ0AoHlGySHDYxDmG8BZmGYpeVVz4o3uVWwkDef+g34pDR2a03hGUN2Op7NUgdkb4K4q8U66yqGOKwXk5lCq23BNs6tjNLoNpjRNxF4E877LDbwSVIqw1wWLTgxAoGBANTsE+72wfSLXA/bSMqWgyaz25sPX0pJPvWt7VaUhSjGYaGFVsNobOXFeh6nH7kNJw26dUjGEwmpL82AbQywPJSbHBPnMHQcu380TbuCqNlBN4WIM4vmLcaqQL1Ch0RIrF1um/MYhYkUuwjcQJUALXyP+VLDFU3RjGoPav2f8E1C";
    private static final String TEST_PUBLIC_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAykojWCnOdPxyVIGAR/M+Mgdcv2kWUDjDxQ3lBzsPpjlys5YfB3HrC1brB/rbDZKKH79Lsihe+XMIWCg0oGaV1pjU/Dip1fPj2UMliX4TiIThuayvDapieMDn7Lpv39x3bjzBQnWlpJIVmokUnJe2xy3f+pRcFbxUZwYyOoiZWkD3ph29l2GTHNFUGpxMW7ceeLE0eJPMndHXy+cpZ2+J3ZCC6P8PYHufkZ5JwhKf+STsQL4gdZvm27xBJZjA9xC0SEnaoS6bzMHfSM29dNdPhtBZFzINGLQQcd8n8RNYzlsCHkMMiubZR+GaRhkOVS649rHEQO2iWFa4+dLTlzsFzwIDAQAB";

    JwtService jwtService;
    NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        var props = new AppProperties();
        props.getJwt().setPrivateKey(TEST_PRIVATE_KEY);
        props.getJwt().setPublicKey(TEST_PUBLIC_KEY);
        props.getJwt().setAccessTokenTtl(Duration.ofMinutes(15));
        jwtService = new JwtService(props);

        byte[] pubBytes = Base64.getDecoder().decode(TEST_PUBLIC_KEY);
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(pubBytes));
        decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Test
    void mintAccessToken_includesSubEmailPermissions() throws Exception {
        var user = new User();
        user.setEmail("alice@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        var userId = UUID.fromString("01906a42-0000-7000-8000-000000000001");
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
