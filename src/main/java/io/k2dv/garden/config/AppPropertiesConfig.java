package io.k2dv.garden.config;

import io.k2dv.garden.payment.config.StripeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProperties.class, StripeProperties.class})
public class AppPropertiesConfig {
}
