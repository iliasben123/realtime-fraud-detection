package com.frauddetection.api.domain.model;

import com.frauddetection.api.domain.exception.InsufficientPrivilegesException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewPolicyTest {

    private static final Reviewer ANALYST = new Reviewer("alice", Set.of(ReviewerRole.ANALYST));
    private static final Reviewer SUPERVISOR = new Reviewer("bob", Set.of(ReviewerRole.ANALYST, ReviewerRole.SUPERVISOR));
    private static final Reviewer NOBODY = new Reviewer("eve", Set.of());

    @ParameterizedTest
    @EnumSource(value = AlertStatus.class, names = {"UNDER_REVIEW", "CONFIRMED_FRAUD", "FALSE_POSITIVE"})
    void analyst_can_review_confirm_and_dismiss(AlertStatus target) {
        assertThat(ReviewPolicy.canTransitionTo(ANALYST, target)).isTrue();
        assertThatCode(() -> ReviewPolicy.require(ANALYST, target)).doesNotThrowAnyException();
    }

    @Test
    void only_supervisor_can_block() {
        assertThat(ReviewPolicy.canTransitionTo(ANALYST, AlertStatus.BLOCKED)).isFalse();
        assertThat(ReviewPolicy.canTransitionTo(SUPERVISOR, AlertStatus.BLOCKED)).isTrue();

        assertThatThrownBy(() -> ReviewPolicy.require(ANALYST, AlertStatus.BLOCKED))
                .isInstanceOf(InsufficientPrivilegesException.class)
                .hasMessageContaining("alice")
                .hasMessageContaining("SUPERVISOR");
    }

    @ParameterizedTest
    @EnumSource(AlertStatus.class)
    void user_without_role_can_do_nothing(AlertStatus target) {
        assertThat(ReviewPolicy.canTransitionTo(NOBODY, target)).isFalse();
    }

    @Test
    void reviewer_requires_a_username() {
        assertThatThrownBy(() -> new Reviewer(" ", Set.of(ReviewerRole.ANALYST)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
