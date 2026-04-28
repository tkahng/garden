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
}
