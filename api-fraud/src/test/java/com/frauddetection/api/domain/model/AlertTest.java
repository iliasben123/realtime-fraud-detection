package com.frauddetection.api.domain.model;

import com.frauddetection.api.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    static Alert openAlert() {
        return new Alert(UUID.randomUUID(), UUID.randomUUID(), "HIGH_AMOUNT", "Montant élevé",
                AlertSeverity.HIGH, new BigDecimal("60.00"), AlertStatus.OPEN, CREATED, CREATED, null, null);
    }

    @Test
    void transition_produces_new_instance_with_review_metadata() {
        Alert open = openAlert();

        Alert confirmed = open.transitionTo(AlertStatus.CONFIRMED_FRAUD, "analyst", "client contacté", NOW);

        assertThat(confirmed).isNotSameAs(open);
        assertThat(confirmed.status()).isEqualTo(AlertStatus.CONFIRMED_FRAUD);
        assertThat(confirmed.reviewedBy()).isEqualTo("analyst");
        assertThat(confirmed.reviewComment()).isEqualTo("client contacté");
        assertThat(confirmed.updatedAt()).isEqualTo(NOW);
        assertThat(confirmed.createdAt()).isEqualTo(CREATED);
        assertThat(confirmed.id()).isEqualTo(open.id());
        // l'original est inchangé
        assertThat(open.status()).isEqualTo(AlertStatus.OPEN);
    }

    @Test
    void forbidden_transition_throws() {
        Alert blocked = openAlert().transitionTo(AlertStatus.BLOCKED, "a", null, NOW);

        assertThatThrownBy(() -> blocked.transitionTo(AlertStatus.OPEN, "a", null, NOW))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("BLOCKED -> OPEN");
    }

    @Test
    void status_change_builds_audit_entry() {
        Alert open = openAlert();
        Alert reviewed = open.transitionTo(AlertStatus.UNDER_REVIEW, "analyst", "en cours", NOW);

        AlertStatusChange change = AlertStatusChange.of(open, reviewed);

        assertThat(change.alertId()).isEqualTo(open.id());
        assertThat(change.previousStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(change.newStatus()).isEqualTo(AlertStatus.UNDER_REVIEW);
        assertThat(change.changedBy()).isEqualTo("analyst");
        assertThat(change.changedAt()).isEqualTo(NOW);
    }
}
