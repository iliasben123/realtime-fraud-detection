package com.frauddetection.api.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Transaction analysée par le job Spark (lecture seule côté API). */
public record Transaction(
        UUID id,
        int step,
        Instant eventTime,
        Instant processedAt,
        TransactionType type,
        BigDecimal amount,
        String nameOrig,
        BigDecimal oldBalanceOrig,
        BigDecimal newBalanceOrig,
        String nameDest,
        BigDecimal oldBalanceDest,
        BigDecimal newBalanceDest,
        boolean fraud,
        boolean flaggedFraud,
        BigDecimal riskScore,
        boolean suspicious,
        BigDecimal mlScore,        // probabilité de fraude du modèle MLlib (x100), null si non scoré
        String modelVersion
) {
}
