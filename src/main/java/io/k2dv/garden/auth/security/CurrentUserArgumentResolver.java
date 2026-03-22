package io.k2dv.garden.auth.security;

import io.k2dv.garden.shared.exception.UnauthorizedException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectProvider<UserRepository> userRepositoryProvider;

    public CurrentUserArgumentResolver(ObjectProvider<UserRepository> userRepositoryProvider) {
        this.userRepositoryProvider = userRepositoryProvider;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && parameter.getParameterType().equals(User.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new UnauthorizedException("NOT_AUTHENTICATED", "Authentication required");
        }
        String userId = jwtAuth.getToken().getSubject();
        UserRepository userRepo = userRepositoryProvider.getObject();
        return userRepo.findById(UUID.fromString(userId))
            .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "Authenticated user not found"));
    }
}
