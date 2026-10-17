package com.frauddetection.api.adapter.in.web.security;

import com.frauddetection.api.domain.model.Reviewer;
import com.frauddetection.api.domain.model.ReviewerRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.EnumSet;
import java.util.Set;

/** Traduit l'utilisateur authentifié par Spring Security en relecteur du domaine. */
public final class ReviewerMapper {

    private ReviewerMapper() {
    }

    public static Reviewer from(Authentication authentication) {
        Set<ReviewerRole> roles = EnumSet.noneOf(ReviewerRole.class);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name.startsWith("ROLE_")) {
                try {
                    roles.add(ReviewerRole.valueOf(name.substring("ROLE_".length())));
                } catch (IllegalArgumentException ignored) {
                    // autorité étrangère au domaine : ignorée
                }
            }
        }
        return new Reviewer(authentication.getName(), roles);
    }
}
