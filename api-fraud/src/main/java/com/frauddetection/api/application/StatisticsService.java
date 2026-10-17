package com.frauddetection.api.application;

import com.frauddetection.api.domain.model.AlertStatistics;
import com.frauddetection.api.domain.port.in.StatisticsUseCase;
import com.frauddetection.api.domain.port.out.AlertRepository;
import com.frauddetection.api.domain.port.out.TransactionRepository;

public class StatisticsService implements StatisticsUseCase {

    private final TransactionRepository transactions;
    private final AlertRepository alerts;

    public StatisticsService(TransactionRepository transactions, AlertRepository alerts) {
        this.transactions = transactions;
        this.alerts = alerts;
    }

    @Override
    public AlertStatistics overview() {
        return new AlertStatistics(
                transactions.count(),
                transactions.countSuspicious(),
                transactions.countFraud(),
                transactions.countSuspiciousAndFraud(),
                alerts.count(),
                alerts.countByStatus(),
                alerts.countBySeverity(),
                alerts.countByRule());
    }
}
