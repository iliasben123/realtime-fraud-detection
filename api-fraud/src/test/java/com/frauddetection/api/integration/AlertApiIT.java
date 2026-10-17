package com.frauddetection.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Scénario de bout en bout HTTP -> service -> JPA -> PostgreSQL. */
class AlertApiIT extends PostgresIntegrationTest {

    @Autowired TestRestTemplate http;

    @Test
    void review_workflow_end_to_end() {
        UUID tx = insertTransaction("TRANSFER", new BigDecimal("400000.00"), "C_ORIG", "C_MULE",
                true, true, new BigDecimal("95"), T0);
        UUID alertId = insertAlert(tx, "ACCOUNT_DRAIN", "CRITICAL", new BigDecimal("85"), "OPEN", T0);
        insertAlert(tx, "HIGH_AMOUNT", "HIGH", new BigDecimal("60"), "OPEN", T0);

        // Liste filtrée
        JsonNode page = http.getForObject("/api/v1/alerts?status=OPEN&severity=CRITICAL", JsonNode.class);
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.at("/content/0/alert/id").asText()).isEqualTo(alertId.toString());
        assertThat(page.at("/content/0/transaction/nameDest").asText()).isEqualTo("C_MULE");

        // Transition OPEN -> UNDER_REVIEW
        ResponseEntity<JsonNode> reviewed = patchStatus(alertId,
                "{\"status\":\"UNDER_REVIEW\",\"reviewedBy\":\"analyst\",\"comment\":\"en cours\"}");
        assertThat(reviewed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reviewed.getBody().get("status").asText()).isEqualTo("UNDER_REVIEW");

        // Transition UNDER_REVIEW -> BLOCKED
        ResponseEntity<JsonNode> blocked = patchStatus(alertId,
                "{\"status\":\"BLOCKED\",\"reviewedBy\":\"supervisor\",\"comment\":\"compte bloqué\"}");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blocked.getBody().get("allowedTransitions")).isEmpty();

        // Transition interdite depuis un état terminal -> 409 Problem Details
        ResponseEntity<JsonNode> refused = patchStatus(alertId, "{\"status\":\"OPEN\"}");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("currentStatus").asText()).isEqualTo("BLOCKED");

        // Historique persisté dans l'ordre
        JsonNode history = http.getForObject("/api/v1/alerts/{id}/history", JsonNode.class, alertId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("newStatus").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(history.get(1).get("newStatus").asText()).isEqualTo("BLOCKED");
        assertThat(history.get(1).get("changedBy").asText()).isEqualTo("supervisor");

        // Détail de la transaction avec ses alertes
        JsonNode txDetail = http.getForObject("/api/v1/transactions/{id}", JsonNode.class, tx);
        assertThat(txDetail.get("alerts")).hasSize(2);

        // Statistiques
        JsonNode stats = http.getForObject("/api/v1/stats/overview", JsonNode.class);
        assertThat(stats.get("totalTransactions").asLong()).isEqualTo(1);
        assertThat(stats.get("truePositives").asLong()).isEqualTo(1);
        assertThat(stats.get("recall").asDouble()).isEqualTo(1.0);
        assertThat(stats.at("/alertsByStatus/BLOCKED").asLong()).isEqualTo(1);
        assertThat(stats.at("/alertsByStatus/OPEN").asLong()).isEqualTo(1);
    }

    @Test
    void unknown_alert_returns_404_problem_detail() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/v1/alerts/{id}", JsonNode.class, UUID.randomUUID());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Ressource introuvable");
    }

    @Test
    void actuator_health_is_up() {
        JsonNode health = http.getForObject("/actuator/health", JsonNode.class);
        assertThat(health.get("status").asText()).isEqualTo("UP");
        assertThat(health.at("/components/db/status").asText()).isEqualTo("UP");
    }

    private ResponseEntity<JsonNode> patchStatus(UUID alertId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/v1/alerts/{id}/status", HttpMethod.PATCH,
                new HttpEntity<>(body, headers), JsonNode.class, alertId);
    }
}
