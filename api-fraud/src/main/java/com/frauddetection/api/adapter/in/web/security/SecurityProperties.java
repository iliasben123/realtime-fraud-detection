package com.frauddetection.api.adapter.in.web.security;

import com.frauddetection.api.domain.model.ReviewerRole;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Paramètres de sécurité (préfixe {@code app.security}) : secret de signature des JWT, durée de vie
 * des jetons et comptes utilisateurs (mots de passe bcrypt). En production, les comptes viendraient
 * d'un annuaire ou d'un fournisseur d'identité ; ici ils sont déclarés dans la configuration.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String jwtSecret, Duration tokenTtl, List<User> users) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes().length < 32) {
            throw new IllegalStateException("app.security.jwt-secret doit faire au moins 32 octets (HS256)");
        }
        tokenTtl = tokenTtl == null ? Duration.ofHours(8) : tokenTtl;
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record User(String username, String password, List<ReviewerRole> roles) {
    }
}
