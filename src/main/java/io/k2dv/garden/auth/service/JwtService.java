package io.k2dv.garden.auth.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.user.model.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class JwtService {

    private final NimbusJwtEncoder encoder;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        byte[] keyBytes = Base64.getDecoder().decode(props.getJwt().getSecret());
        var key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    public String mintAccessToken(User user, List<String> permissions) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
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
