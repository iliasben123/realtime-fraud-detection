package com.frauddetection.api.domain.model;

/** Rôles des utilisateurs qui traitent les alertes. */
public enum ReviewerRole {
    /** Analyse les alertes : revue, confirmation, classement en faux positif. */
    ANALYST,
    /** Analyste disposant en plus du droit de bloquer un compte (statut BLOCKED). */
    SUPERVISOR
}
