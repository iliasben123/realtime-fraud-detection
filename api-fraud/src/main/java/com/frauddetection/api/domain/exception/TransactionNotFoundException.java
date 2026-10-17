package com.frauddetection.api.domain.exception;

import java.util.UUID;

public class TransactionNotFoundException extends DomainException {

    private final UUID transactionId;

    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction introuvable : " + transactionId);
        this.transactionId = transactionId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }
}
