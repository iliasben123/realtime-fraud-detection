package com.frauddetection.api.adapter.in.web.dto;

import com.frauddetection.api.domain.model.AlertSeverity;
import com.frauddetection.api.domain.model.AlertStatus;

import java.util.Map;

public record StatisticsResponse(
        long totalTransactions,
        long suspiciousTransactions,
        long labeledFraudTransactions,
        long truePositives,
        double precision,
        double recall,
        long totalAlerts,
        Map<AlertStatus, Long> alertsByStatus,
        Map<AlertSeverity, Long> alertsBySeverity,
        Map<String, Long> alertsByRule
) {
}
