package io.k2dv.garden.payment.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "stripe")
@Validated
@Getter
@Setter
public class StripeProperties {

    @NotBlank
    private String secretKey;

    @NotBlank
    private String webhookSecret;
}
