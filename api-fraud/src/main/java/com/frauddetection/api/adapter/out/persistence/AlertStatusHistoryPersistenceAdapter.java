package com.frauddetection.api.adapter.out.persistence;

import com.frauddetection.api.adapter.out.persistence.repository.AlertStatusHistoryJpaRepository;
import com.frauddetection.api.domain.model.AlertStatusChange;
import com.frauddetection.api.domain.port.out.AlertStatusHistoryRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class AlertStatusHistoryPersistenceAdapter implements AlertStatusHistoryRepository {

    private final AlertStatusHistoryJpaRepository history;

    AlertStatusHistoryPersistenceAdapter(AlertStatusHistoryJpaRepository history) {
        this.history = history;
    }

    @Override
    public AlertStatusChange append(AlertStatusChange change) {
        return PersistenceMapper.toDomain(history.save(PersistenceMapper.toEntity(change)));
    }

    @Override
    public List<AlertStatusChange> findByAlertId(UUID alertId) {
        return history.findByAlertIdOrderByChangedAtAscIdAsc(alertId).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }
}
