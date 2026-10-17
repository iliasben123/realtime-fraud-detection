package com.frauddetection.api.domain.port.out;

import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.AlertDetail;
import com.frauddetection.api.domain.model.AlertFilter;
import com.frauddetection.api.domain.model.AlertSeverity;
import com.frauddetection.api.domain.model.AlertStatus;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Port sortant : persistance des alertes. */
public interface AlertRepository {

    Optional<Alert> findById(UUID alertId);

    Optional<AlertDetail> findDetailById(UUID alertId);

    PageResult<AlertDetail> search(AlertFilter filter, PageQuery page);

    List<Alert> findByTransactionId(UUID transactionId);

    Alert save(Alert alert);

    long count();

    Map<AlertStatus, Long> countByStatus();

    Map<AlertSeverity, Long> countBySeverity();

    Map<String, Long> countByRule();
}
