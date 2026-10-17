package com.frauddetection.api.domain.port.out;

import com.frauddetection.api.domain.model.AlertStatusChange;

import java.util.List;
import java.util.UUID;

/** Port sortant : piste d'audit des changements de statut. */
public interface AlertStatusHistoryRepository {

    AlertStatusChange append(AlertStatusChange change);

    List<AlertStatusChange> findByAlertId(UUID alertId);
}
