package com.frauddetection.api.adapter.in.web.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Émission des jetons d'accès (JWT HS256) pour un utilisateur authentifié. */
@Component
public class JwtTokenService {

    static final String ISSUER = "fraud-api";

    private final JwtEncoder encoder;
    private final SecurityProperties props;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, SecurityProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public Token issue(Authentication authentication) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(props.tokenTtl());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .claim(SecurityConfiguration.ROLES_CLAIM, roles)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Token(token, expiresAt, authentication.getName(), roles);
    }

    public record Token(String accessToken, Instant expiresAt, String username, List<String> roles) {
    }
}
