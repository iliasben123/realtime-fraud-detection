package com.frauddetection.api.adapter.in.web.dto;

import com.frauddetection.api.domain.model.AlertStatus;

import java.time.Instant;

public record StatusHistoryResponse(
        Long id,
        AlertStatus previousStatus,
        AlertStatus newStatus,
        String changedBy,
        String comment,
        Instant changedAt
) {
}
