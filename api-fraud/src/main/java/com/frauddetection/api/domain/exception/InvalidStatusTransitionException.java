package com.frauddetection.api.domain.exception;

import com.frauddetection.api.domain.model.AlertStatus;

import java.util.UUID;

public class InvalidStatusTransitionException extends DomainException {

    private final UUID alertId;
    private final AlertStatus from;
    private final AlertStatus to;

    public InvalidStatusTransitionException(UUID alertId, AlertStatus from, AlertStatus to) {
        super(String.format("Transition %s -> %s non autorisée pour l'alerte %s (transitions possibles : %s)",
                from, to, alertId, from.allowedTransitions()));
        this.alertId = alertId;
        this.from = from;
        this.to = to;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public AlertStatus getFrom() {
        return from;
    }

    public AlertStatus getTo() {
        return to;
    }
}
