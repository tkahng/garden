package io.k2dv.garden.auth.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        var authorities = (permissions == null ? List.<String>of() : permissions).stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
