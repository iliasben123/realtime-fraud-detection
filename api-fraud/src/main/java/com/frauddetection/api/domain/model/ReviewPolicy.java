package com.frauddetection.api.domain.model;

import com.frauddetection.api.domain.exception.InsufficientPrivilegesException;

/**
 * Règles d'habilitation sur le cycle de vie des alertes (règle métier, indépendante du mécanisme
 * d'authentification) : bloquer un compte est une décision lourde réservée aux superviseurs.
 */
public final class ReviewPolicy {

    private ReviewPolicy() {
    }

    public static boolean canTransitionTo(Reviewer reviewer, AlertStatus target) {
        if (target == AlertStatus.BLOCKED) {
            return reviewer.isSupervisor();
        }
        return reviewer.has(ReviewerRole.ANALYST) || reviewer.isSupervisor();
    }

    /** @throws InsufficientPrivilegesException si le relecteur n'a pas le droit de viser ce statut */
    public static void require(Reviewer reviewer, AlertStatus target) {
        if (!canTransitionTo(reviewer, target)) {
            throw new InsufficientPrivilegesException(reviewer, target);
        }
    }
}
