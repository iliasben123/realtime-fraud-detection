package com.frauddetection.api.integration;

import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.AlertDetail;
import com.frauddetection.api.domain.model.AlertFilter;
import com.frauddetection.api.domain.model.AlertSeverity;
import com.frauddetection.api.domain.model.AlertStatus;
import com.frauddetection.api.domain.model.AlertStatusChange;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;
import com.frauddetection.api.domain.model.Transaction;
import com.frauddetection.api.domain.model.TransactionFilter;
import com.frauddetection.api.domain.model.TransactionType;
import com.frauddetection.api.domain.port.out.AlertRepository;
import com.frauddetection.api.domain.port.out.AlertStatusHistoryRepository;
import com.frauddetection.api.domain.port.out.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Vérifie les adaptateurs JPA contre un vrai PostgreSQL : enums natifs, specifications, agrégations. */
class PersistenceAdaptersIT extends PostgresIntegrationTest {

    @Autowired AlertRepository alerts;
    @Autowired TransactionRepository transactions;
    @Autowired AlertStatusHistoryRepository history;

    private static final BigDecimal BIG = new BigDecimal("250000.00");
    private static final BigDecimal SMALL = new BigDecimal("1200.00");

    @Test
    void search_alerts_filters_on_status_severity_rule_account_and_amount() {
        UUID bigTx = insertTransaction("TRANSFER", BIG, "C_ORIG", "C_DEST", true, true, new BigDecimal("95"), T0);
        UUID smallTx = insertTransaction("PAYMENT", SMALL, "C_OTHER", "M_SHOP", false, true, new BigDecimal("25"), T0);
        UUID a1 = insertAlert(bigTx, "HIGH_AMOUNT", "HIGH", new BigDecimal("60"), "OPEN", T0);
        insertAlert(bigTx, "ACCOUNT_DRAIN", "CRITICAL", new BigDecimal("85"), "OPEN", T0.plusSeconds(1));
        insertAlert(smallTx, "BALANCE_INCONSISTENCY", "LOW", new BigDecimal("25"), "FALSE_POSITIVE", T0.plusSeconds(2));

        PageQuery page = new PageQuery(0, 10, "createdAt", PageQuery.SortDirection.DESC);

        PageResult<AlertDetail> open = alerts.search(
                new AlertFilter(AlertStatus.OPEN, null, null, null, null, null, null, null), page);
        assertThat(open.totalElements()).isEqualTo(2);
        assertThat(open.content()).allSatisfy(d -> {
            assertThat(d.alert().status()).isEqualTo(AlertStatus.OPEN);
            assertThat(d.transaction().id()).isEqualTo(bigTx);       // jointure chargée
            assertThat(d.transaction().type()).isEqualTo(TransactionType.TRANSFER);
        });
        // tri createdAt DESC : ACCOUNT_DRAIN (T0+1s) avant HIGH_AMOUNT (T0)
        assertThat(open.content().get(0).alert().ruleName()).isEqualTo("ACCOUNT_DRAIN");

        PageResult<AlertDetail> critical = alerts.search(
                new AlertFilter(null, AlertSeverity.CRITICAL, null, null, null, null, null, null), page);
        assertThat(critical.content()).extracting(d -> d.alert().ruleName()).containsExactly("ACCOUNT_DRAIN");

        PageResult<AlertDetail> byRule = alerts.search(
                new AlertFilter(null, null, "high_amount", null, null, null, null, null), page);
        assertThat(byRule.content()).extracting(d -> d.alert().id()).containsExactly(a1);

        PageResult<AlertDetail> byDestAccount = alerts.search(
                new AlertFilter(null, null, null, "M_SHOP", null, null, null, null), page);
        assertThat(byDestAccount.content()).extracting(d -> d.transaction().id()).containsExactly(smallTx);

        PageResult<AlertDetail> byAmount = alerts.search(
                new AlertFilter(null, null, null, null, new BigDecimal("100000"), null, null, null), page);
        assertThat(byAmount.totalElements()).isEqualTo(2);

        PageResult<AlertDetail> byPeriod = alerts.search(
                new AlertFilter(null, null, null, null, null, null, T0.plusSeconds(2), T0.plus(Duration.ofHours(1))), page);
        assertThat(byPeriod.content()).extracting(d -> d.alert().ruleName()).containsExactly("BALANCE_INCONSISTENCY");
    }

    @Test
    void search_alerts_paginates_and_sorts_by_risk_score() {
        UUID tx = insertTransaction("CASH_OUT", BIG, "C1", "C2", false, true, new BigDecimal("90"), T0);
        insertAlert(tx, "R1", "LOW", new BigDecimal("10"), "OPEN", T0);
        insertAlert(tx, "R2", "HIGH", new BigDecimal("70"), "OPEN", T0);
        insertAlert(tx, "R3", "MEDIUM", new BigDecimal("40"), "OPEN", T0);

        PageResult<AlertDetail> first = alerts.search(AlertFilter.none(),
                new PageQuery(0, 2, "riskScore", PageQuery.SortDirection.DESC));
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).extracting(d -> d.alert().ruleName()).containsExactly("R2", "R3");

        PageResult<AlertDetail> second = alerts.search(AlertFilter.none(),
                new PageQuery(1, 2, "riskScore", PageQuery.SortDirection.DESC));
        assertThat(second.content()).extracting(d -> d.alert().ruleName()).containsExactly("R1");
    }

    @Test
    void save_persists_status_change_and_history_roundtrip() {
        UUID tx = insertTransaction("TRANSFER", BIG, "C1", "C2", true, true, new BigDecimal("85"), T0);
        UUID alertId = insertAlert(tx, "ACCOUNT_DRAIN", "CRITICAL", new BigDecimal("85"), "OPEN", T0);

        Alert before = alerts.findById(alertId).orElseThrow();
        assertThat(before.status()).isEqualTo(AlertStatus.OPEN);
        assertThat(before.severity()).isEqualTo(AlertSeverity.CRITICAL);

        Alert after = before.transitionTo(AlertStatus.CONFIRMED_FRAUD, "analyst", "confirmé", T0.plusSeconds(60));
        Alert saved = alerts.save(after);
        history.append(AlertStatusChange.of(before, saved));

        Alert reloaded = alerts.findById(alertId).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(AlertStatus.CONFIRMED_FRAUD);
        assertThat(reloaded.reviewedBy()).isEqualTo("analyst");
        assertThat(reloaded.reviewComment()).isEqualTo("confirmé");
        // updated_at est posé par le trigger PostgreSQL (now()), pas par l'horloge applicative
        assertThat(reloaded.updatedAt()).isAfter(before.updatedAt());
        assertThat(saved.updatedAt()).isEqualTo(reloaded.updatedAt());   // valeur relue après l'UPDATE

        List<AlertStatusChange> changes = history.findByAlertId(alertId);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).previousStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(changes.get(0).newStatus()).isEqualTo(AlertStatus.CONFIRMED_FRAUD);
        assertThat(changes.get(0).id()).isNotNull();

        assertThat(alerts.findByTransactionId(tx)).extracting(Alert::id).containsExactly(alertId);
    }

    @Test
    void sort_by_optional_column_puts_nulls_last() {
        UUID scored = insertTransaction("TRANSFER", BIG, "C_ML", "C_X", true, true, new BigDecimal("98"), T0);
        jdbc.update("UPDATE fraud.transactions SET ml_score = 98.16, model_version = 'v1' WHERE transaction_id = ?", scored);
        insertTransaction("PAYMENT", SMALL, "C_NO_ML", "M_Y", false, false, BigDecimal.ZERO, T0);   // ml_score NULL

        PageResult<Transaction> desc = transactions.search(TransactionFilter.none(),
                new PageQuery(0, 10, "mlScore", PageQuery.SortDirection.DESC));
        assertThat(desc.content()).extracting(Transaction::nameOrig).containsExactly("C_ML", "C_NO_ML");
        assertThat(desc.content().get(0).mlScore()).isEqualByComparingTo("98.16");
        assertThat(desc.content().get(0).modelVersion()).isEqualTo("v1");

        PageResult<Transaction> asc = transactions.search(TransactionFilter.none(),
                new PageQuery(0, 10, "mlScore", PageQuery.SortDirection.ASC));
        assertThat(asc.content()).extracting(Transaction::nameOrig).containsExactly("C_ML", "C_NO_ML");
    }

    @Test
    void grouped_counts_return_native_enums_and_rules() {
        UUID tx = insertTransaction("TRANSFER", BIG, "C1", "C2", true, true, new BigDecimal("85"), T0);
        insertAlert(tx, "HIGH_AMOUNT", "HIGH", new BigDecimal("60"), "OPEN", T0);
        insertAlert(tx, "ACCOUNT_DRAIN", "CRITICAL", new BigDecimal("85"), "BLOCKED", T0);
        insertAlert(tx, "FLAGGED_BY_SOURCE", "HIGH", new BigDecimal("70"), "OPEN", T0);

        assertThat(alerts.count()).isEqualTo(3);
        assertThat(alerts.countByStatus()).containsExactlyInAnyOrderEntriesOf(
                Map.of(AlertStatus.OPEN, 2L, AlertStatus.BLOCKED, 1L));
        assertThat(alerts.countBySeverity()).containsExactlyInAnyOrderEntriesOf(
                Map.of(AlertSeverity.HIGH, 2L, AlertSeverity.CRITICAL, 1L));
        assertThat(alerts.countByRule()).containsEntry("HIGH_AMOUNT", 1L).hasSize(3);
    }

    @Test
    void transaction_repository_filters_and_counts() {
        insertTransaction("TRANSFER", BIG, "C_A", "C_B", true, true, new BigDecimal("95"), T0);
        insertTransaction("CASH_OUT", new BigDecimal("50000.00"), "C_C", "C_D", false, true, new BigDecimal("60"), T0.plusSeconds(10));
        insertTransaction("PAYMENT", SMALL, "C_E", "M_F", false, false, BigDecimal.ZERO, T0.plusSeconds(20));

        assertThat(transactions.count()).isEqualTo(3);
        assertThat(transactions.countSuspicious()).isEqualTo(2);
        assertThat(transactions.countFraud()).isEqualTo(1);
        assertThat(transactions.countSuspiciousAndFraud()).isEqualTo(1);

        PageQuery page = new PageQuery(0, 10, "eventTime", PageQuery.SortDirection.ASC);
        PageResult<Transaction> suspicious = transactions.search(
                new TransactionFilter(true, null, null, null, null, null, null, null, null), page);
        assertThat(suspicious.content()).extracting(Transaction::nameOrig).containsExactly("C_A", "C_C");

        PageResult<Transaction> transfersAbove = transactions.search(
                new TransactionFilter(null, null, TransactionType.TRANSFER, null, new BigDecimal("100000"), null, null, null, null), page);
        assertThat(transfersAbove.content()).extracting(Transaction::nameOrig).containsExactly("C_A");

        PageResult<Transaction> byAccount = transactions.search(
                new TransactionFilter(null, null, null, "M_F", null, null, null, null, null), page);
        assertThat(byAccount.content()).extracting(Transaction::type).containsExactly(TransactionType.PAYMENT);
    }
}
