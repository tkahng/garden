package io.k2dv.garden.config;

import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.user.model.User;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * Test-only override for CurrentUserArgumentResolver.
 * Returns a stub User with a fixed UUID so slice tests that use @CurrentUser
 * don't require a real JwtAuthenticationToken in the security context.
 *
 * <p>Registered at index 0 via WebMvcConfigurer so it takes precedence over
 * the production CurrentUserArgumentResolver bean.
 */
@TestConfiguration
public class TestCurrentUserConfig implements WebMvcConfigurer {

    public static final UUID STUB_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(0, new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class)
                    && parameter.getParameterType().isAssignableFrom(User.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return createStubUser();
            }
        });
    }

    private static User createStubUser() {
        User user = new User();
        try {
            Field idField = io.k2dv.garden.shared.model.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, STUB_USER_ID);
        } catch (Exception e) {
            throw new IllegalStateException("Could not set stub user id", e);
        }
        return user;
    }
}
