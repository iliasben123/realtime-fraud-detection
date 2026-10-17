package com.frauddetection.api.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Entrée de la piste d'audit des changements de statut d'une alerte. */
public record AlertStatusChange(
        Long id,
        UUID alertId,
        AlertStatus previousStatus,
        AlertStatus newStatus,
        String changedBy,
        String comment,
        Instant changedAt
) {

    public static AlertStatusChange of(Alert before, Alert after) {
        return new AlertStatusChange(null, after.id(), before.status(), after.status(),
                after.reviewedBy(), after.reviewComment(), after.updatedAt());
    }
}
