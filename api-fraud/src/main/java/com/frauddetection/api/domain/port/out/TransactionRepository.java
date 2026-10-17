package com.frauddetection.api.domain.port.out;

import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;
import com.frauddetection.api.domain.model.Transaction;
import com.frauddetection.api.domain.model.TransactionFilter;

import java.util.Optional;
import java.util.UUID;

/** Port sortant : lecture des transactions analysées. */
public interface TransactionRepository {

    Optional<Transaction> findById(UUID transactionId);

    PageResult<Transaction> search(TransactionFilter filter, PageQuery page);

    long count();

    long countSuspicious();

    long countFraud();

    /** Transactions à la fois suspectes (règles) et frauduleuses (label) : vrais positifs. */
    long countSuspiciousAndFraud();
}
