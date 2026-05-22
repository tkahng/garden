package io.k2dv.garden.auth.security;

import io.k2dv.garden.auth.service.ImpersonationService;
import io.k2dv.garden.shared.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;

/**
 * Validates impersonation JWTs against the DB record on every request.
 * Runs after BearerTokenAuthenticationFilter so the JWT is already decoded.
 *
 * If the JWT carries an "impersonatedBy" claim, the token hash must exist in
 * auth.impersonation_tokens and not be expired. The first matching request
 * also sets used_at for audit purposes.
 */
@RequiredArgsConstructor
public class ImpersonationTokenValidationFilter extends OncePerRequestFilter {

    private final ImpersonationService impersonationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Principal principal = request.getUserPrincipal();
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            if (jwt.hasClaim("impersonatedBy")) {
                String tokenHash = ImpersonationService.sha256(jwt.getTokenValue());
                try {
                    impersonationService.validateAndRecordUse(tokenHash);
                } catch (UnauthorizedException ex) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                        "{\"error\":\"" + ex.getErrorCode() + "\",\"message\":\"" + ex.getMessage() + "\",\"status\":401}");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
