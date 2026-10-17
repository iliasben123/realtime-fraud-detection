package com.frauddetection.api.adapter.out.persistence;

import com.frauddetection.api.adapter.out.persistence.entity.AlertEntity;
import com.frauddetection.api.adapter.out.persistence.entity.TransactionEntity;
import com.frauddetection.api.domain.model.AlertFilter;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Traduction du filtre métier en critères JPA (jointure transaction incluse). */
final class AlertSpecifications {

    private AlertSpecifications() {
    }

    static Specification<AlertEntity> from(AlertFilter f) {
        return (root, query, cb) -> {
            // Lecture : fetch join (transaction chargée en une requête) ; count : simple jointure
            Join<AlertEntity, TransactionEntity> tx;
            if (query != null && !Long.class.equals(query.getResultType())) {
                Fetch<AlertEntity, TransactionEntity> fetch = root.fetch("transaction", JoinType.INNER);
                @SuppressWarnings("unchecked")
                Join<AlertEntity, TransactionEntity> asJoin = (Join<AlertEntity, TransactionEntity>) fetch;
                tx = asJoin;
            } else {
                tx = root.join("transaction", JoinType.INNER);
            }

            List<Predicate> p = new ArrayList<>();
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.severity() != null) {
                p.add(cb.equal(root.get("severity"), f.severity()));
            }
            if (f.ruleName() != null && !f.ruleName().isBlank()) {
                p.add(cb.equal(root.get("ruleName"), f.ruleName().trim().toUpperCase()));
            }
            if (f.account() != null && !f.account().isBlank()) {
                String account = f.account().trim();
                p.add(cb.or(cb.equal(tx.get("nameOrig"), account), cb.equal(tx.get("nameDest"), account)));
            }
            if (f.minAmount() != null) {
                p.add(cb.greaterThanOrEqualTo(tx.get("amount"), f.minAmount()));
            }
            if (f.minRiskScore() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("riskScore"), f.minRiskScore()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("createdAt"), f.to()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
