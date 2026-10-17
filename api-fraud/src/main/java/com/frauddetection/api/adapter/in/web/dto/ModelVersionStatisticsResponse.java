package com.frauddetection.api.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ModelVersionStatisticsResponse(
        String modelVersion,
        long scoredTransactions,
        long labeledFraud,
        long mlAlerts,
        long mlAlertsOnLabeledFraud,
        long mlAlertsConfirmed,
        long mlAlertsFalsePositive,
        long mlAlertsPending,
        double precisionVsLabel,
        double recallVsLabel,
        double precisionVsAnalysts,
        double reviewRate,
        BigDecimal avgMlScore,
        BigDecimal avgMlScoreOnFraud,
        Instant firstScoredAt,
        Instant lastScoredAt
) {
}
