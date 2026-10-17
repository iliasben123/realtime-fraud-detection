package com.frauddetection.api.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cycle de vie d'une alerte. Les transitions autorisées sont une règle métier :
 *
 * <pre>
 *   OPEN ──► UNDER_REVIEW ──► CONFIRMED_FRAUD ──► BLOCKED
 *     │            │                 ▲
 *     │            └──► FALSE_POSITIVE│
 *     └──────────────────────────────┘ (raccourcis depuis OPEN)
 * </pre>
 *
 * FALSE_POSITIVE et BLOCKED sont des états terminaux.
 */
public enum AlertStatus {
    OPEN,
    UNDER_REVIEW,
    CONFIRMED_FRAUD,
    FALSE_POSITIVE,
    BLOCKED;

    private static final Map<AlertStatus, Set<AlertStatus>> TRANSITIONS = Map.of(
            OPEN, EnumSet.of(UNDER_REVIEW, CONFIRMED_FRAUD, FALSE_POSITIVE, BLOCKED),
            UNDER_REVIEW, EnumSet.of(CONFIRMED_FRAUD, FALSE_POSITIVE, BLOCKED),
            CONFIRMED_FRAUD, EnumSet.of(BLOCKED),
            FALSE_POSITIVE, EnumSet.noneOf(AlertStatus.class),
            BLOCKED, EnumSet.noneOf(AlertStatus.class)
    );

    public boolean canTransitionTo(AlertStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }

    public Set<AlertStatus> allowedTransitions() {
        return Set.copyOf(TRANSITIONS.get(this));
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
