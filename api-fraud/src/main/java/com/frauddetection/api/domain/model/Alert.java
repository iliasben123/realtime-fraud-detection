package com.frauddetection.api.domain.model;

import com.frauddetection.api.domain.exception.InvalidStatusTransitionException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Alerte de fraude générée par le moteur de règles Spark.
 * Objet immuable : un changement de statut produit une nouvelle instance.
 */
public record Alert(
        UUID id,
        UUID transactionId,
        String ruleName,
        String description,
        AlertSeverity severity,
        BigDecimal riskScore,
        AlertStatus status,
        Instant createdAt,
        Instant updatedAt,
        String reviewedBy,
        String reviewComment
) {

    public Alert {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(ruleName, "ruleName");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(status, "status");
    }

    /**
     * Applique une transition de statut en respectant le cycle de vie défini par {@link AlertStatus}.
     *
     * @throws InvalidStatusTransitionException si la transition n'est pas autorisée
     */
    public Alert transitionTo(AlertStatus target, String reviewer, String comment, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(id, status, target);
        }
        return new Alert(id, transactionId, ruleName, description, severity, riskScore,
                target, createdAt, now, reviewer, comment);
    }

    public boolean isOpen() {
        return status == AlertStatus.OPEN;
    }
}
