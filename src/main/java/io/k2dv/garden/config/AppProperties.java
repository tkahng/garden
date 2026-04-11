package io.k2dv.garden.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
@Validated
@Getter
@Setter
public class AppProperties {

    @Valid
    private Jwt jwt = new Jwt();

    @Valid
    private Superuser superuser = new Superuser();

    @NotBlank
    private String frontendUrl = "http://localhost:3000";

    // Optional: if set, new quote submissions send a notification to this address
    private String adminNotificationEmail;

    @Getter
    @Setter
    public static class Jwt {
        @NotBlank
        private String secret;

        private Duration accessTokenTtl = Duration.ofMinutes(15);

        private Duration refreshTokenTtl = Duration.ofDays(30);

        private Duration emailVerificationTtl = Duration.ofHours(24);

        private Duration passwordResetTtl = Duration.ofHours(24);
    }

    @Getter
    @Setter
    public static class Superuser {
        @NotBlank
        private String email = "owner@garden.local";

        @NotBlank
        private String password = "changeme";
    }
}
