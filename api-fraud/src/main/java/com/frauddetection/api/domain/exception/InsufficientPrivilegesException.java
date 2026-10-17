package com.frauddetection.api.domain.exception;

import com.frauddetection.api.domain.model.AlertStatus;
import com.frauddetection.api.domain.model.Reviewer;
import com.frauddetection.api.domain.model.ReviewerRole;

public class InsufficientPrivilegesException extends DomainException {

    private final String username;
    private final AlertStatus target;

    public InsufficientPrivilegesException(Reviewer reviewer, AlertStatus target) {
        super(String.format("L'utilisateur %s (rôles %s) n'est pas autorisé à passer une alerte au statut %s"
                        + (target == AlertStatus.BLOCKED ? " : rôle %s requis" : ""),
                reviewer.username(), reviewer.roles(), target, ReviewerRole.SUPERVISOR));
        this.username = reviewer.username();
        this.target = target;
    }

    public String getUsername() {
        return username;
    }

    public AlertStatus getTarget() {
        return target;
    }
}
