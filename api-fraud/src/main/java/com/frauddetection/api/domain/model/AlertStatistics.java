package com.frauddetection.api.domain.model;

import java.util.Map;

/** Indicateurs globaux de la plateforme (qualité de détection, répartition des alertes). */
public record AlertStatistics(
        long totalTransactions,
        long suspiciousTransactions,
        long labeledFraudTransactions,
        long truePositives,
        long totalAlerts,
        Map<AlertStatus, Long> alertsByStatus,
        Map<AlertSeverity, Long> alertsBySeverity,
        Map<String, Long> alertsByRule
) {

    /** Part des transactions suspectes qui sont réellement frauduleuses (label PaySim). */
    public double precision() {
        return suspiciousTransactions == 0 ? 0d : (double) truePositives / suspiciousTransactions;
    }

    /** Part des fraudes réelles détectées par les règles. */
    public double recall() {
        return labeledFraudTransactions == 0 ? 0d : (double) truePositives / labeledFraudTransactions;
    }
}
