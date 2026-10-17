package com.frauddetection.api.application;

import com.frauddetection.api.domain.exception.AlertNotFoundException;
import com.frauddetection.api.domain.exception.InvalidStatusTransitionException;
import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.AlertSeverity;
import com.frauddetection.api.domain.model.AlertStatus;
import com.frauddetection.api.domain.model.AlertStatusChange;
import com.frauddetection.api.domain.port.out.AlertRepository;
import com.frauddetection.api.domain.port.out.AlertStatusHistoryRepository;
import com.frauddetection.api.domain.port.out.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T09:30:00Z");

    @Mock AlertRepository alerts;
    @Mock AlertStatusHistoryRepository history;

    /** UnitOfWork « passe-plat » : exécute le travail sans transaction réelle. */
    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };

    private AlertService service;

    @BeforeEach
    void setUp() {
        service = new AlertService(alerts, history, unitOfWork, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Alert alert(AlertStatus status) {
        Instant created = Instant.parse("2026-02-01T08:00:00Z");
        return new Alert(UUID.randomUUID(), UUID.randomUUID(), "ACCOUNT_DRAIN", "vidage de compte",
                AlertSeverity.CRITICAL, new BigDecimal("85.00"), status, created, created, null, null);
    }

    @Test
    void changeStatus_saves_alert_and_appends_history() {
        Alert open = alert(AlertStatus.OPEN);
        when(alerts.findById(open.id())).thenReturn(Optional.of(open));
        when(alerts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Alert result = service.changeStatus(open.id(), AlertStatus.CONFIRMED_FRAUD, "analyst", "confirmé");

        assertThat(result.status()).isEqualTo(AlertStatus.CONFIRMED_FRAUD);
        assertThat(result.updatedAt()).isEqualTo(NOW);     // le repository (mock) renvoie l'alerte telle que sauvée
        assertThat(result.reviewedBy()).isEqualTo("analyst");

        ArgumentCaptor<AlertStatusChange> captor = ArgumentCaptor.forClass(AlertStatusChange.class);
        verify(history).append(captor.capture());
        AlertStatusChange change = captor.getValue();
        assertThat(change.alertId()).isEqualTo(open.id());
        assertThat(change.previousStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(change.newStatus()).isEqualTo(AlertStatus.CONFIRMED_FRAUD);
        assertThat(change.comment()).isEqualTo("confirmé");
        assertThat(change.changedAt()).isEqualTo(NOW);
    }

    @Test
    void changeStatus_rejects_forbidden_transition_without_persisting() {
        Alert blocked = alert(AlertStatus.BLOCKED);
        when(alerts.findById(blocked.id())).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> service.changeStatus(blocked.id(), AlertStatus.OPEN, "analyst", null))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(alerts, never()).save(any());
        verify(history, never()).append(any());
    }

    @Test
    void changeStatus_unknown_alert_throws_not_found() {
        UUID unknown = UUID.randomUUID();
        when(alerts.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(unknown, AlertStatus.BLOCKED, "analyst", null))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void getById_unknown_alert_throws_not_found() {
        UUID unknown = UUID.randomUUID();
        when(alerts.findDetailById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(unknown)).isInstanceOf(AlertNotFoundException.class);
    }

    @Test
    void history_requires_existing_alert() {
        UUID unknown = UUID.randomUUID();
        when(alerts.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(unknown)).isInstanceOf(AlertNotFoundException.class);
        verify(history, never()).findByAlertId(any());
    }
}
