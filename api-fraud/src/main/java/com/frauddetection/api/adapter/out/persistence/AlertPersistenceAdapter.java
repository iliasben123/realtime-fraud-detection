package com.frauddetection.api.adapter.out.persistence;

import com.frauddetection.api.adapter.out.persistence.entity.AlertEntity;
import com.frauddetection.api.adapter.out.persistence.entity.TransactionEntity;
import com.frauddetection.api.adapter.out.persistence.repository.AlertJpaRepository;
import com.frauddetection.api.adapter.out.persistence.repository.TransactionJpaRepository;
import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.AlertDetail;
import com.frauddetection.api.domain.model.AlertFilter;
import com.frauddetection.api.domain.model.AlertSeverity;
import com.frauddetection.api.domain.model.AlertStatus;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;
import com.frauddetection.api.domain.port.out.AlertRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Component
class AlertPersistenceAdapter implements AlertRepository {

    private final AlertJpaRepository alerts;
    private final TransactionJpaRepository transactions;

    AlertPersistenceAdapter(AlertJpaRepository alerts, TransactionJpaRepository transactions) {
        this.alerts = alerts;
        this.transactions = transactions;
    }

    @Override
    public Optional<Alert> findById(UUID alertId) {
        return alerts.findById(alertId).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<AlertDetail> findDetailById(UUID alertId) {
        return alerts.findWithTransactionByAlertId(alertId).map(PersistenceMapper::toDetail);
    }

    @Override
    public PageResult<AlertDetail> search(AlertFilter filter, PageQuery page) {
        return PersistenceMapper.toPageResult(
                alerts.findAll(AlertSpecifications.from(filter, page), PersistenceMapper.toPageable(page)),
                PersistenceMapper::toDetail);
    }

    @Override
    public List<Alert> findByTransactionId(UUID transactionId) {
        return alerts.findByTransactionTransactionIdOrderByCreatedAtAsc(transactionId).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Alert save(Alert alert) {
        AlertEntity entity = alerts.findById(alert.id())
                .map(existing -> {
                    existing.applyReview(alert.status(), alert.reviewedBy(), alert.reviewComment());
                    return existing;
                })
                .orElseGet(() -> {
                    TransactionEntity tx = transactions.getReferenceById(alert.transactionId());
                    return new AlertEntity(alert.id(), tx, alert.ruleName(), alert.description(),
                            alert.severity(), alert.riskScore(), alert.status(), alert.createdAt(),
                            alert.updatedAt(), alert.reviewedBy(), alert.reviewComment());
                });
        // saveAndFlush : exécute l'UPDATE immédiatement pour relire updated_at généré par la base
        return PersistenceMapper.toDomain(alerts.saveAndFlush(entity));
    }

    @Override
    public long count() {
        return alerts.count();
    }

    @Override
    public Map<AlertStatus, Long> countByStatus() {
        return toCounts(alerts.countGroupedByStatus(), AlertStatus.class::cast);
    }

    @Override
    public Map<AlertSeverity, Long> countBySeverity() {
        return toCounts(alerts.countGroupedBySeverity(), AlertSeverity.class::cast);
    }

    @Override
    public Map<String, Long> countByRule() {
        return toCounts(alerts.countGroupedByRule(), String.class::cast);
    }

    private static <K> Map<K, Long> toCounts(List<Object[]> rows, Function<Object, K> key) {
        Map<K, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(key.apply(row[0]), ((Number) row[1]).longValue());
        }
        return result;
    }
}
