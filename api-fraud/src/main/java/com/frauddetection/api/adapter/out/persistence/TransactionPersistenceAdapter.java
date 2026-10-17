package com.frauddetection.api.adapter.out.persistence;

import com.frauddetection.api.adapter.out.persistence.repository.TransactionJpaRepository;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;
import com.frauddetection.api.domain.model.Transaction;
import com.frauddetection.api.domain.model.TransactionFilter;
import com.frauddetection.api.domain.port.out.TransactionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class TransactionPersistenceAdapter implements TransactionRepository {

    private final TransactionJpaRepository transactions;

    TransactionPersistenceAdapter(TransactionJpaRepository transactions) {
        this.transactions = transactions;
    }

    @Override
    public Optional<Transaction> findById(UUID transactionId) {
        return transactions.findById(transactionId).map(PersistenceMapper::toDomain);
    }

    @Override
    public PageResult<Transaction> search(TransactionFilter filter, PageQuery page) {
        return PersistenceMapper.toPageResult(
                transactions.findAll(TransactionSpecifications.from(filter, page), PersistenceMapper.toPageable(page)),
                PersistenceMapper::toDomain);
    }

    @Override
    public long count() {
        return transactions.count();
    }

    @Override
    public long countSuspicious() {
        return transactions.countBySuspiciousTrue();
    }

    @Override
    public long countFraud() {
        return transactions.countByFraudTrue();
    }

    @Override
    public long countSuspiciousAndFraud() {
        return transactions.countBySuspiciousTrueAndFraudTrue();
    }
}
