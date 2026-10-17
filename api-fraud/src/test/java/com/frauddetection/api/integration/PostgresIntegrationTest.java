package com.frauddetection.api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Socle des tests d'intégration : un PostgreSQL 16 réel démarré une seule fois par JVM,
 * initialisé avec le même script que le docker-compose (source unique de vérité du schéma).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("frauddb")
            .withUsername("fraud")
            .withPassword("fraud")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("..", "docker", "postgres", "init", "01_schema.sql")),
                    "/docker-entrypoint-initdb.d/01_schema.sql");

    static {
        POSTGRES.start();   // conteneur singleton partagé par toutes les classes *IT (contexte Spring mis en cache)
    }

    protected static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE fraud.alert_status_history, fraud.fraud_alerts, fraud.transactions RESTART IDENTITY");
    }

    // ------------------------------------------------------------------ fixtures

    protected UUID insertTransaction(String type, BigDecimal amount, String nameOrig, String nameDest,
                                     boolean fraud, boolean suspicious, BigDecimal riskScore, Instant eventTime) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fraud.transactions (transaction_id, step, event_time, type, amount, name_orig,
                    old_balance_orig, new_balance_orig, name_dest, old_balance_dest, new_balance_dest,
                    is_fraud, is_flagged_fraud, risk_score, is_suspicious)
                VALUES (?, 1, ?, ?, ?, ?, 1000000, 500000, ?, 0, 0, ?, false, ?, ?)
                """, id, java.sql.Timestamp.from(eventTime), type, amount, nameOrig, nameDest, fraud, riskScore, suspicious);
        return id;
    }

    protected UUID insertAlert(UUID transactionId, String rule, String severity, BigDecimal riskScore,
                               String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO fraud.fraud_alerts (alert_id, transaction_id, rule_name, description, severity,
                    risk_score, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, CAST(? AS fraud.alert_severity), ?, CAST(? AS fraud.alert_status), ?, ?)
                """, id, transactionId, rule, "desc " + rule, severity, riskScore, status,
                java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
        return id;
    }
}
