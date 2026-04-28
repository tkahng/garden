package io.k2dv.garden.config;

import io.k2dv.garden.auth.ratelimit.LoginRateLimiter;
import io.k2dv.garden.auth.security.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final LoginRateLimiter loginRateLimiter;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginRateLimiter)
            .addPathPatterns(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/resend-verification",
                "/api/v1/auth/request-password-reset"
            );
    }
}
