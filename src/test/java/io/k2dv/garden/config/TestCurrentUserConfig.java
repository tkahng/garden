package io.k2dv.garden.config;

import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.user.model.User;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Test-only override for CurrentUserArgumentResolver.
 * Returns a stub User with a fixed UUID so slice tests that use @CurrentUser
 * don't require a real JwtAuthenticationToken in the security context.
 */
@TestConfiguration
public class TestCurrentUserConfig {

    public static final UUID STUB_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Bean
    @Primary
    io.k2dv.garden.auth.security.CurrentUserArgumentResolver currentUserArgumentResolver() {
        return new io.k2dv.garden.auth.security.CurrentUserArgumentResolver(null) {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class)
                    && parameter.getParameterType().equals(User.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                User user = new User();
                try {
                    java.lang.reflect.Field idField = io.k2dv.garden.shared.model.BaseEntity.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(user, STUB_USER_ID);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to set stub user id", e);
                }
                return user;
            }
        };
    }
}
