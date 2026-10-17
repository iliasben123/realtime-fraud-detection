package com.frauddetection.api.adapter.in.web;

import com.frauddetection.api.adapter.in.web.dto.AlertDetailResponse;
import com.frauddetection.api.adapter.in.web.dto.AlertResponse;
import com.frauddetection.api.adapter.in.web.dto.StatisticsResponse;
import com.frauddetection.api.adapter.in.web.dto.StatusHistoryResponse;
import com.frauddetection.api.adapter.in.web.dto.TransactionResponse;
import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.AlertDetail;
import com.frauddetection.api.domain.model.AlertStatistics;
import com.frauddetection.api.domain.model.AlertStatusChange;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.Transaction;

import java.util.Set;

/** Conversions modèle du domaine -> DTO REST. */
final class WebMapper {

    static final Set<String> ALERT_SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "riskScore", "severity", "status", "ruleName");
    static final Set<String> TRANSACTION_SORT_FIELDS =
            Set.of("eventTime", "processedAt", "amount", "riskScore", "type", "step");

    private WebMapper() {
    }

    static AlertResponse toResponse(Alert a) {
        return new AlertResponse(a.id(), a.transactionId(), a.ruleName(), a.description(), a.severity(),
                a.riskScore(), a.status(), a.status().allowedTransitions(), a.createdAt(), a.updatedAt(),
                a.reviewedBy(), a.reviewComment());
    }

    static TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(t.id(), t.step(), t.eventTime(), t.processedAt(), t.type(), t.amount(),
                t.nameOrig(), t.oldBalanceOrig(), t.newBalanceOrig(), t.nameDest(), t.oldBalanceDest(),
                t.newBalanceDest(), t.fraud(), t.flaggedFraud(), t.riskScore(), t.suspicious());
    }

    static AlertDetailResponse toResponse(AlertDetail d) {
        return new AlertDetailResponse(toResponse(d.alert()), toResponse(d.transaction()));
    }

    static StatusHistoryResponse toResponse(AlertStatusChange c) {
        return new StatusHistoryResponse(c.id(), c.previousStatus(), c.newStatus(), c.changedBy(),
                c.comment(), c.changedAt());
    }

    static StatisticsResponse toResponse(AlertStatistics s) {
        return new StatisticsResponse(s.totalTransactions(), s.suspiciousTransactions(),
                s.labeledFraudTransactions(), s.truePositives(), round(s.precision()), round(s.recall()),
                s.totalAlerts(), s.alertsByStatus(), s.alertsBySeverity(), s.alertsByRule());
    }

    static PageQuery toPageQuery(int page, int size, String sortBy, String direction, Set<String> allowedSorts) {
        if (!allowedSorts.contains(sortBy)) {
            throw new IllegalArgumentException(
                    "sortBy invalide : '" + sortBy + "' (valeurs possibles : " + allowedSorts + ")");
        }
        PageQuery.SortDirection dir;
        try {
            dir = PageQuery.SortDirection.valueOf(direction.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("direction invalide : '" + direction + "' (ASC ou DESC)");
        }
        return new PageQuery(page, size, sortBy, dir);
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
