package com.frauddetection.api.domain.model;

import java.util.Objects;
import java.util.Set;

/** Utilisateur authentifié qui prend une décision sur une alerte. */
public record Reviewer(String username, Set<ReviewerRole> roles) {

    public Reviewer {
        Objects.requireNonNull(username, "username");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username est obligatoire");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean has(ReviewerRole role) {
        return roles.contains(role);
    }

    public boolean isSupervisor() {
        return has(ReviewerRole.SUPERVISOR);
    }
}
