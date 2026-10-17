package com.frauddetection.api.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelVersionStatisticsTest {

    private static ModelVersionStatistics stats(long scored, long fraud, long alerts, long onFraud,
                                                long confirmed, long fp, long pending) {
        return new ModelVersionStatistics("v1", scored, fraud, alerts, onFraud, confirmed, fp, pending,
                new BigDecimal("12.34"), new BigDecimal("97.00"), Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void ratios_against_label_and_analysts() {
        ModelVersionStatistics s = stats(10_000, 80, 100, 78, 60, 15, 25);

        assertThat(s.precisionVsLabel()).isCloseTo(0.78, within(1e-9));
        assertThat(s.recallVsLabel()).isCloseTo(78d / 80, within(1e-9));
        assertThat(s.precisionVsAnalysts()).isCloseTo(60d / 75, within(1e-9));
        assertThat(s.reviewRate()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void ratios_are_zero_when_denominators_are_zero() {
        ModelVersionStatistics s = stats(0, 0, 0, 0, 0, 0, 0);

        assertThat(s.precisionVsLabel()).isZero();
        assertThat(s.recallVsLabel()).isZero();
        assertThat(s.precisionVsAnalysts()).isZero();
        assertThat(s.reviewRate()).isZero();
    }

    @Test
    void rule_feedback_ratios() {
        RuleFeedback f = new RuleFeedback("ACCOUNT_DRAIN", 2_076, 78, 50, 1_000, 1_026);

        assertThat(f.precisionVsLabel()).isCloseTo(78d / 2_076, within(1e-9));
        assertThat(f.precisionVsAnalysts()).isCloseTo(50d / 1_050, within(1e-9));
        assertThat(f.reviewRate()).isCloseTo(1_050d / 2_076, within(1e-9));
    }
}
