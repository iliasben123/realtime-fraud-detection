package com.frauddetection.api.application;

import com.frauddetection.api.domain.exception.AlertNotFoundException;
import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.AlertDetail;
import com.frauddetection.api.domain.model.AlertFilter;
import com.frauddetection.api.domain.model.AlertStatus;
import com.frauddetection.api.domain.model.AlertStatusChange;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;
import com.frauddetection.api.domain.model.ReviewPolicy;
import com.frauddetection.api.domain.model.Reviewer;
import com.frauddetection.api.domain.port.in.AlertQueryUseCase;
import com.frauddetection.api.domain.port.in.AlertStatusUseCase;
import com.frauddetection.api.domain.port.out.AlertRepository;
import com.frauddetection.api.domain.port.out.AlertStatusHistoryRepository;
import com.frauddetection.api.domain.port.out.UnitOfWork;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'utilisation autour des alertes. Classe sans dépendance au framework :
 * les ports sortants sont injectés par la configuration (adapter de configuration Spring).
 */
public class AlertService implements AlertQueryUseCase, AlertStatusUseCase {

    private final AlertRepository alerts;
    private final AlertStatusHistoryRepository history;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AlertService(AlertRepository alerts,
                        AlertStatusHistoryRepository history,
                        UnitOfWork unitOfWork,
                        Clock clock) {
        this.alerts = alerts;
        this.history = history;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public PageResult<AlertDetail> search(AlertFilter filter, PageQuery page) {
        return alerts.search(filter == null ? AlertFilter.none() : filter, page);
    }

    @Override
    public AlertDetail getById(UUID alertId) {
        return alerts.findDetailById(alertId).orElseThrow(() -> new AlertNotFoundException(alertId));
    }

    @Override
    public List<AlertStatusChange> history(UUID alertId) {
        alerts.findById(alertId).orElseThrow(() -> new AlertNotFoundException(alertId));
        return history.findByAlertId(alertId);
    }

    @Override
    public Alert changeStatus(UUID alertId, AlertStatus target, Reviewer reviewer, String comment) {
        ReviewPolicy.require(reviewer, target);
        return unitOfWork.execute(() -> {
            Alert current = alerts.findById(alertId).orElseThrow(() -> new AlertNotFoundException(alertId));
            Alert updated = current.transitionTo(target, reviewer.username(), comment, Instant.now(clock));
            Alert saved = alerts.save(updated);
            history.append(AlertStatusChange.of(current, saved));
            return saved;
        });
    }
}
