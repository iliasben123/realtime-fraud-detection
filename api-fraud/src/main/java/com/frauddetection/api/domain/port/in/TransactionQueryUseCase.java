package com.frauddetection.api.domain.port.in;

import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;
import com.frauddetection.api.domain.model.Transaction;
import com.frauddetection.api.domain.model.TransactionFilter;

import java.util.List;
import java.util.UUID;

/** Port entrant : consultation des transactions analysées. */
public interface TransactionQueryUseCase {

    PageResult<Transaction> search(TransactionFilter filter, PageQuery page);

    Transaction getById(UUID transactionId);

    List<Alert> alertsOf(UUID transactionId);
}
