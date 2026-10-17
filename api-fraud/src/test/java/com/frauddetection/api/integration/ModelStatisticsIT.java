package com.frauddetection.api.integration;

import com.frauddetection.api.domain.model.ModelVersionStatistics;
import com.frauddetection.api.domain.model.RuleFeedback;
import com.frauddetection.api.domain.port.out.ModelStatisticsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Agrégations croisant versions de modèle, label et décisions des analystes, sur PostgreSQL réel. */
class ModelStatisticsIT extends PostgresIntegrationTest {

    @Autowired ModelStatisticsRepository repository;

    private UUID scored(String version, BigDecimal mlScore, boolean fraud) {
        UUID id = insertTransaction("TRANSFER", new BigDecimal("1000.00"), "C_O", "C_D", fraud, true,
                mlScore, T0);
        jdbc.update("UPDATE fraud.transactions SET ml_score = ?, model_version = ? WHERE transaction_id = ?",
                mlScore, version, id);
        return id;
    }

    @Test
    void statistics_by_model_version_cross_label_and_analyst_decisions() {
        // v1 : 4 transactions scorées (2 fraudes), 3 alertes ML : 1 confirmée (sur fraude), 1 faux positif, 1 en attente (sur fraude)
        UUID f1 = scored("v1", new BigDecimal("98.00"), true);
        UUID f2 = scored("v1", new BigDecimal("96.00"), true);
        UUID l1 = scored("v1", new BigDecimal("80.00"), false);
        scored("v1", new BigDecimal("2.00"), false);
        insertAlert(f1, "ML_HIGH_RISK", "HIGH", new BigDecimal("75"), "CONFIRMED_FRAUD", T0);
        insertAlert(f2, "ML_HIGH_RISK", "HIGH", new BigDecimal("75"), "UNDER_REVIEW", T0);
        insertAlert(l1, "ML_HIGH_RISK", "HIGH", new BigDecimal("75"), "FALSE_POSITIVE", T0);
        insertAlert(l1, "HIGH_AMOUNT", "HIGH", new BigDecimal("60"), "OPEN", T0);    // autre règle : ignorée ici

        // v2 : 1 transaction scorée, aucune alerte ML
        scored("v2", new BigDecimal("5.00"), false);

        // transaction non scorée (pas de modèle) : ignorée
        insertTransaction("PAYMENT", new BigDecimal("10.00"), "C_X", "M_Y", false, false, BigDecimal.ZERO, T0);

        List<ModelVersionStatistics> stats = repository.byModelVersion();
        assertThat(stats).extracting(ModelVersionStatistics::modelVersion).containsExactlyInAnyOrder("v1", "v2");

        ModelVersionStatistics v1 = stats.stream().filter(s -> s.modelVersion().equals("v1")).findFirst().orElseThrow();
        assertThat(v1.scoredTransactions()).isEqualTo(4);
        assertThat(v1.labeledFraud()).isEqualTo(2);
        assertThat(v1.mlAlerts()).isEqualTo(3);
        assertThat(v1.mlAlertsOnLabeledFraud()).isEqualTo(2);
        assertThat(v1.mlAlertsConfirmed()).isEqualTo(1);
        assertThat(v1.mlAlertsFalsePositive()).isEqualTo(1);
        assertThat(v1.mlAlertsPending()).isEqualTo(1);
        assertThat(v1.precisionVsLabel()).isCloseTo(2d / 3, within(1e-9));
        assertThat(v1.recallVsLabel()).isCloseTo(1.0, within(1e-9));
        assertThat(v1.precisionVsAnalysts()).isCloseTo(0.5, within(1e-9));
        assertThat(v1.avgMlScore()).isEqualByComparingTo("69.00");          // (98+96+80+2)/4
        assertThat(v1.avgMlScoreOnFraud()).isEqualByComparingTo("97.00");   // (98+96)/2
        assertThat(v1.firstScoredAt()).isNotNull();

        ModelVersionStatistics v2 = stats.stream().filter(s -> s.modelVersion().equals("v2")).findFirst().orElseThrow();
        assertThat(v2.scoredTransactions()).isEqualTo(1);
        assertThat(v2.mlAlerts()).isZero();
        assertThat(v2.avgMlScoreOnFraud()).isNull();
    }

    @Test
    void feedback_by_rule() {
        UUID fraud = insertTransaction("CASH_OUT", new BigDecimal("500.00"), "C_A", "C_B", true, true, new BigDecimal("85"), T0);
        UUID legit = insertTransaction("CASH_OUT", new BigDecimal("500.00"), "C_C", "C_D", false, true, new BigDecimal("85"), T0);
        insertAlert(fraud, "ACCOUNT_DRAIN", "CRITICAL", new BigDecimal("85"), "BLOCKED", T0);
        insertAlert(legit, "ACCOUNT_DRAIN", "CRITICAL", new BigDecimal("85"), "FALSE_POSITIVE", T0);
        insertAlert(legit, "HIGH_AMOUNT", "HIGH", new BigDecimal("60"), "OPEN", T0);

        List<RuleFeedback> feedback = repository.ruleFeedback();
        assertThat(feedback).extracting(RuleFeedback::ruleName).containsExactlyInAnyOrder("ACCOUNT_DRAIN", "HIGH_AMOUNT");

        RuleFeedback drain = feedback.stream().filter(f -> f.ruleName().equals("ACCOUNT_DRAIN")).findFirst().orElseThrow();
        assertThat(drain.alerts()).isEqualTo(2);
        assertThat(drain.onLabeledFraud()).isEqualTo(1);
        assertThat(drain.confirmed()).isEqualTo(1);           // BLOCKED compte comme confirmé
        assertThat(drain.falsePositive()).isEqualTo(1);
        assertThat(drain.pending()).isZero();
        assertThat(drain.precisionVsAnalysts()).isCloseTo(0.5, within(1e-9));

        RuleFeedback high = feedback.stream().filter(f -> f.ruleName().equals("HIGH_AMOUNT")).findFirst().orElseThrow();
        assertThat(high.pending()).isEqualTo(1);
        assertThat(high.reviewRate()).isZero();
    }
}
