package io.k2dv.garden.auth.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.user.model.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Responsible for minting RS256-signed JWTs used as short-lived access tokens.
 * The RSA key pair is loaded once at startup from application configuration; both
 * regular access tokens and impersonation tokens (which carry an {@code impersonatedBy}
 * claim) are produced here.
 */
@Service
public class JwtService {

    private final NimbusJwtEncoder encoder;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            byte[] privBytes = Base64.getDecoder().decode(props.getJwt().getPrivateKey());
            RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            byte[] pubBytes = Base64.getDecoder().decode(props.getJwt().getPublicKey());
            RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
            this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JWT encoder", e);
        }
    }

    /**
     * Issues a standard access token embedding the user's identity, email, email-verified
     * timestamp (if present), and their resolved permission set. The token lifetime is
     * governed by the configured {@code jwt.access-token-ttl}.
     */
    public String mintAccessToken(User user, List<String> permissions) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plus(props.getJwt().getAccessTokenTtl()))
            .claim("email", user.getEmail())
            .claim("permissions", permissions);

        if (user.getEmailVerifiedAt() != null) {
            claims.claim("emailVerifiedAt", user.getEmailVerifiedAt().toString());
        }

        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /**
     * Issues a short-lived impersonation token that acts on behalf of a target user.
     * The {@code impersonatedBy} claim records the admin's UUID for audit trails, and
     * the token intentionally carries an empty permissions list to limit blast radius.
     */
    public String mintImpersonationToken(User user, java.util.UUID adminUserId, java.time.Instant expiresAt) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(user.getId().toString())
            .issuedAt(Instant.now())
            .expiresAt(expiresAt)
            .claim("email", user.getEmail())
            .claim("permissions", java.util.List.of())
            .claim("impersonatedBy", adminUserId.toString())
            .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
