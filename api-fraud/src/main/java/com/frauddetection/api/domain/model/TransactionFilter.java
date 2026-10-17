package com.frauddetection.api.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Critères de recherche de transactions. Tous les champs sont optionnels (null = non filtré). */
public record TransactionFilter(
        Boolean suspicious,
        Boolean fraud,
        TransactionType type,
        String account,          // compte émetteur ou destinataire
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal minRiskScore,
        Instant from,
        Instant to
) {
    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null, null, null, null, null, null, null);
    }
}
