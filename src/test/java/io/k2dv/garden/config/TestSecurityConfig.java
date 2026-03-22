package io.k2dv.garden.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security configuration. Permits all requests so foundation tests
 * can focus on domain behaviour without dealing with OAuth2 or JWT setup.
 * Method security pre/post annotations are disabled so @Authenticated and
 * @HasPermission on controllers are bypassed in slice tests.
 */
@TestConfiguration
@EnableMethodSecurity(prePostEnabled = false)
public class TestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
