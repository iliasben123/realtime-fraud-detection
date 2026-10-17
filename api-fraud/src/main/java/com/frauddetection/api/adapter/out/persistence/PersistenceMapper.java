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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
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
                e.isFraud(), e.isFlaggedFraud(), e.getRiskScore(), e.isSuspicious(),
                e.getMlScore(), e.getModelVersion());
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

    /** Pagination sans tri : l'ordre est posé par la Specification (cf. applyOrder) pour gérer NULLS LAST. */
    static Pageable toPageable(PageQuery q) {
        return PageRequest.of(q.page(), q.size());
    }

    /**
     * Tri avec NULLS LAST : les colonnes optionnelles (ml_score, reviewed_by...) ne doivent pas remonter
     * en tête d'un tri décroissant (comportement PostgreSQL par défaut). Spring Data ne propage pas la
     * précédence des NULL vers les Criteria Queries, on passe par l'API Hibernate. Ignoré pour le count.
     */
    static void applyOrder(CriteriaQuery<?> query, CriteriaBuilder cb, Root<?> root, PageQuery q, String tieBreaker) {
        if (query == null || Long.class.equals(query.getResultType()) || q == null) {
            return;
        }
        HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
        boolean asc = q.direction() == PageQuery.SortDirection.ASC;
        Expression<?> column = root.get(q.sortBy());
        List<Order> orders = new ArrayList<>();
        orders.add(asc ? hcb.asc(column, false) : hcb.desc(column, false));
        // Second critère (date) : ordre total, donc pagination stable entre deux requêtes
        if (tieBreaker != null && !tieBreaker.equals(q.sortBy())) {
            orders.add(hcb.desc(root.get(tieBreaker), false));
        }
        query.orderBy(orders);
    }

    static <E, D> PageResult<D> toPageResult(Page<E> page, Function<E, D> mapper) {
        return new PageResult<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
