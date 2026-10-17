package com.frauddetection.api.adapter.out.persistence;

import com.frauddetection.api.adapter.out.persistence.entity.TransactionEntity;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.TransactionFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<TransactionEntity> from(TransactionFilter f, PageQuery page) {
        return (root, query, cb) -> {
            PersistenceMapper.applyOrder(query, cb, root, page);
            List<Predicate> p = new ArrayList<>();
            if (f.suspicious() != null) {
                p.add(cb.equal(root.get("suspicious"), f.suspicious()));
            }
            if (f.fraud() != null) {
                p.add(cb.equal(root.get("fraud"), f.fraud()));
            }
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.account() != null && !f.account().isBlank()) {
                String account = f.account().trim();
                p.add(cb.or(cb.equal(root.get("nameOrig"), account), cb.equal(root.get("nameDest"), account)));
            }
            if (f.minAmount() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("amount"), f.minAmount()));
            }
            if (f.maxAmount() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("amount"), f.maxAmount()));
            }
            if (f.minRiskScore() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("riskScore"), f.minRiskScore()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("eventTime"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("eventTime"), f.to()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
