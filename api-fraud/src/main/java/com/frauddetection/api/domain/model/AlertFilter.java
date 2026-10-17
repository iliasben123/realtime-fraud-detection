package com.frauddetection.api.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Critères de recherche d'alertes. Tous les champs sont optionnels (null = non filtré). */
public record AlertFilter(
        AlertStatus status,
        AlertSeverity severity,
        String ruleName,
        String account,          // compte émetteur ou destinataire de la transaction
        BigDecimal minAmount,
        BigDecimal minRiskScore,
        Instant from,
        Instant to
) {
    public static AlertFilter none() {
        return new AlertFilter(null, null, null, null, null, null, null, null);
    }
}
