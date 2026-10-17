package com.frauddetection.api.domain.exception;

import java.util.UUID;

public class AlertNotFoundException extends DomainException {

    private final UUID alertId;

    public AlertNotFoundException(UUID alertId) {
        super("Alerte introuvable : " + alertId);
        this.alertId = alertId;
    }

    public UUID getAlertId() {
        return alertId;
    }
}
