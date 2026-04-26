package io.k2dv.garden.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
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

    @Valid
    private Automation automation = new Automation();

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

    @Getter
    @Setter
    public static class Automation {
        /** How long a cart must be idle before sending an abandoned-cart reminder. */
        private Duration abandonedCartDelay = Duration.ofHours(1);

        /** Available stock (on_hand - committed) at or below this triggers a low-stock alert. */
        private int lowStockThreshold = 5;

        /** Cumulative spend in USD at or above this threshold triggers the "vip" tag. */
        private BigDecimal vipSpendThreshold = new BigDecimal("500");
    }
}
