package io.k2dv.garden.auth.handler;

import io.k2dv.garden.auth.model.Identity;
import io.k2dv.garden.auth.model.IdentityProvider;
import io.k2dv.garden.auth.model.TokenType;
import io.k2dv.garden.auth.repository.IdentityRepository;
import io.k2dv.garden.auth.service.JwtService;
import io.k2dv.garden.auth.service.TokenService;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.iam.service.IamService;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepo;
    private final IdentityRepository identityRepo;
    private final IamService iamService;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final AppProperties props;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String googleSub = oidcUser.getSubject();

        User user = identityRepo.findByProviderAndAccountId(IdentityProvider.GOOGLE, googleSub)
            .map(identity -> userRepo.findById(identity.getUserId()).orElseThrow())
            .orElseGet(() -> createOrLinkUser(oidcUser));

        List<String> permissions = iamService.loadPermissionsForUser(user.getId());
        String accessToken = jwtService.mintAccessToken(user, permissions);
        String refreshToken = tokenService.createToken(
            user.getId(), TokenType.REFRESH_TOKEN, props.getJwt().getRefreshTokenTtl());

        String redirect = props.getFrontendUrl() + "/auth/callback"
            + "?accessToken=" + accessToken
            + "&refreshToken=" + refreshToken;
        getRedirectStrategy().sendRedirect(request, response, redirect);
    }

    private User createOrLinkUser(OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        String googleSub = oidcUser.getSubject();

        User user = userRepo.findByEmail(email).orElseGet(() -> {
            var newUser = new User();
            newUser.setEmail(email);
            newUser.setFirstName(oidcUser.getGivenName() != null ? oidcUser.getGivenName() : "");
            newUser.setLastName(oidcUser.getFamilyName() != null ? oidcUser.getFamilyName() : "");
            newUser.setStatus(UserStatus.ACTIVE);
            newUser.setEmailVerifiedAt(Instant.now());
            var saved = userRepo.save(newUser);
            iamService.assignRoleByName(saved.getId(), "CUSTOMER");
            return saved;
        });

        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
            user.setStatus(UserStatus.ACTIVE);
            user = userRepo.save(user);
        }

        Identity identity = new Identity();
        identity.setUserId(user.getId());
        identity.setProvider(IdentityProvider.GOOGLE);
        identity.setAccountId(googleSub);
        identityRepo.save(identity);

        return user;
    }
}
