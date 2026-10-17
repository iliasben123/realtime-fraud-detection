package com.frauddetection.api.adapter.in.web.dto;

import com.frauddetection.api.domain.model.AlertSeverity;
import com.frauddetection.api.domain.model.AlertStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID transactionId,
        String ruleName,
        String description,
        AlertSeverity severity,
        BigDecimal riskScore,
        AlertStatus status,
        Set<AlertStatus> allowedTransitions,
        Instant createdAt,
        Instant updatedAt,
        String reviewedBy,
        String reviewComment
) {
}
