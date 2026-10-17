package com.frauddetection.api.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class AlertStatusTest {

    @ParameterizedTest
    @CsvSource({
            "OPEN, UNDER_REVIEW, true",
            "OPEN, CONFIRMED_FRAUD, true",
            "OPEN, FALSE_POSITIVE, true",
            "OPEN, BLOCKED, true",
            "UNDER_REVIEW, CONFIRMED_FRAUD, true",
            "UNDER_REVIEW, FALSE_POSITIVE, true",
            "UNDER_REVIEW, BLOCKED, true",
            "UNDER_REVIEW, OPEN, false",
            "CONFIRMED_FRAUD, BLOCKED, true",
            "CONFIRMED_FRAUD, FALSE_POSITIVE, false",
            "CONFIRMED_FRAUD, OPEN, false",
            "FALSE_POSITIVE, OPEN, false",
            "FALSE_POSITIVE, BLOCKED, false",
            "BLOCKED, OPEN, false",
            "BLOCKED, CONFIRMED_FRAUD, false",
    })
    void transitions(AlertStatus from, AlertStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(AlertStatus.class)
    void no_self_transition(AlertStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    void terminal_states() {
        assertThat(AlertStatus.FALSE_POSITIVE.isTerminal()).isTrue();
        assertThat(AlertStatus.BLOCKED.isTerminal()).isTrue();
        assertThat(AlertStatus.OPEN.isTerminal()).isFalse();
        assertThat(AlertStatus.UNDER_REVIEW.isTerminal()).isFalse();
        assertThat(AlertStatus.CONFIRMED_FRAUD.isTerminal()).isFalse();
    }

    @Test
    void null_target_is_rejected() {
        assertThat(AlertStatus.OPEN.canTransitionTo(null)).isFalse();
    }
}
