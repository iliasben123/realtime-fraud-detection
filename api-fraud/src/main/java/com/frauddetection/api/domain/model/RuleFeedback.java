package com.frauddetection.api.domain.model;

/** Retour des analystes et du label sur les alertes d'une règle (métier ou ML). */
public record RuleFeedback(
        String ruleName,
        long alerts,
        long onLabeledFraud,
        long confirmed,         // CONFIRMED_FRAUD ou BLOCKED
        long falsePositive,     // FALSE_POSITIVE
        long pending            // OPEN ou UNDER_REVIEW
) {

    public double precisionVsLabel() {
        return ModelVersionStatistics.ratio(onLabeledFraud, alerts);
    }

    public double precisionVsAnalysts() {
        return ModelVersionStatistics.ratio(confirmed, confirmed + falsePositive);
    }

    public double reviewRate() {
        return ModelVersionStatistics.ratio(alerts - pending, alerts);
    }
}
