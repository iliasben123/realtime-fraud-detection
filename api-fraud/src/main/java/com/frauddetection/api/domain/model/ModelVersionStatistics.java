package com.frauddetection.api.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Qualité d'une version du modèle MLlib, mesurée de deux façons :
 * <ul>
 *   <li>contre le label du dataset (vérité terrain PaySim) : précision / rappel « offline » ;</li>
 *   <li>contre les décisions des analystes sur les alertes {@code ML_HIGH_RISK} : précision « terrain »,
 *       la seule disponible en production où il n'y a pas de label.</li>
 * </ul>
 */
public record ModelVersionStatistics(
        String modelVersion,
        long scoredTransactions,
        long labeledFraud,              // transactions is_fraud parmi les scorées
        long mlAlerts,                  // alertes ML_HIGH_RISK émises
        long mlAlertsOnLabeledFraud,    // ... dont la transaction est une fraude labellisée
        long mlAlertsConfirmed,         // ... passées CONFIRMED_FRAUD ou BLOCKED par un analyste
        long mlAlertsFalsePositive,     // ... classées FALSE_POSITIVE
        long mlAlertsPending,           // ... encore OPEN ou UNDER_REVIEW
        BigDecimal avgMlScore,
        BigDecimal avgMlScoreOnFraud,
        Instant firstScoredAt,
        Instant lastScoredAt
) {

    public double precisionVsLabel() {
        return ratio(mlAlertsOnLabeledFraud, mlAlerts);
    }

    public double recallVsLabel() {
        return ratio(mlAlertsOnLabeledFraud, labeledFraud);
    }

    /** Part des alertes ML revues par un analyste qui se sont avérées fondées. */
    public double precisionVsAnalysts() {
        return ratio(mlAlertsConfirmed, mlAlertsConfirmed + mlAlertsFalsePositive);
    }

    /** Part des alertes ML déjà traitées par les analystes. */
    public double reviewRate() {
        return ratio(mlAlerts - mlAlertsPending, mlAlerts);
    }

    static double ratio(long num, long den) {
        return den == 0 ? 0d : (double) num / den;
    }
}
