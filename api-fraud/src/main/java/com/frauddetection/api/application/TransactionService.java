package com.frauddetection.api.application;

import com.frauddetection.api.domain.exception.TransactionNotFoundException;
import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;
import com.frauddetection.api.domain.model.Transaction;
import com.frauddetection.api.domain.model.TransactionFilter;
import com.frauddetection.api.domain.port.in.TransactionQueryUseCase;
import com.frauddetection.api.domain.port.out.AlertRepository;
import com.frauddetection.api.domain.port.out.TransactionRepository;

import java.util.List;
import java.util.UUID;

public class TransactionService implements TransactionQueryUseCase {

    private final TransactionRepository transactions;
    private final AlertRepository alerts;

    public TransactionService(TransactionRepository transactions, AlertRepository alerts) {
        this.transactions = transactions;
        this.alerts = alerts;
    }

    @Override
    public PageResult<Transaction> search(TransactionFilter filter, PageQuery page) {
        return transactions.search(filter == null ? TransactionFilter.none() : filter, page);
    }

    @Override
    public Transaction getById(UUID transactionId) {
        return transactions.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Override
    public List<Alert> alertsOf(UUID transactionId) {
        getById(transactionId);
        return alerts.findByTransactionId(transactionId);
    }
}
