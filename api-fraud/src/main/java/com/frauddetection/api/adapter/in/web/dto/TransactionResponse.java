package com.frauddetection.api.adapter.in.web.dto;

import com.frauddetection.api.domain.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
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
        boolean suspicious
) {
}
