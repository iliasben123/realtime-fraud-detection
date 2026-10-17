package com.frauddetection.api.adapter.out.persistence;

import com.frauddetection.api.adapter.out.persistence.entity.AlertEntity;
import com.frauddetection.api.adapter.out.persistence.entity.AlertStatusHistoryEntity;
import com.frauddetection.api.adapter.out.persistence.entity.TransactionEntity;
import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.AlertDetail;
import com.frauddetection.api.domain.model.AlertStatusChange;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;
import com.frauddetection.api.domain.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

/** Conversions entités JPA <-> modèle du domaine, et pagination Spring Data <-> domaine. */
final class PersistenceMapper {

    private PersistenceMapper() {
    }

    static Transaction toDomain(TransactionEntity e) {
        return new Transaction(
                e.getTransactionId(), e.getStep(), e.getEventTime(), e.getProcessedAt(), e.getType(),
                e.getAmount(), e.getNameOrig(), e.getOldBalanceOrig(), e.getNewBalanceOrig(),
                e.getNameDest(), e.getOldBalanceDest(), e.getNewBalanceDest(),
                e.isFraud(), e.isFlaggedFraud(), e.getRiskScore(), e.isSuspicious());
    }

    static Alert toDomain(AlertEntity e) {
        return new Alert(
                e.getAlertId(), e.getTransaction().getTransactionId(), e.getRuleName(), e.getDescription(),
                e.getSeverity(), e.getRiskScore(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getReviewedBy(), e.getReviewComment());
    }

    static AlertDetail toDetail(AlertEntity e) {
        return new AlertDetail(toDomain(e), toDomain(e.getTransaction()));
    }

    static AlertStatusChange toDomain(AlertStatusHistoryEntity e) {
        return new AlertStatusChange(e.getId(), e.getAlertId(), e.getPreviousStatus(), e.getNewStatus(),
                e.getChangedBy(), e.getComment(), e.getChangedAt());
    }

    static AlertStatusHistoryEntity toEntity(AlertStatusChange c) {
        return new AlertStatusHistoryEntity(c.alertId(), c.previousStatus(), c.newStatus(),
                c.changedBy(), c.comment(), c.changedAt());
    }

    static Pageable toPageable(PageQuery q) {
        Sort.Direction direction = q.direction() == PageQuery.SortDirection.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(q.page(), q.size(), Sort.by(direction, q.sortBy()));
    }

    static <E, D> PageResult<D> toPageResult(Page<E> page, Function<E, D> mapper) {
        return new PageResult<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
