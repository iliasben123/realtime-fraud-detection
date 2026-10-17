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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Scénario de bout en bout HTTP (JWT) -> service -> JPA -> PostgreSQL. */
class AlertApiIT extends PostgresIntegrationTest {

    @Autowired TestRestTemplate http;

    // ------------------------------------------------------------------ authentification

    private String login(String username, String password) {
        ResponseEntity<JsonNode> response = http.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password), JsonNode.class);
        assertThat(response.getStatusCode()).as("login " + username).isEqualTo(HttpStatus.OK);
        return response.getBody().get("accessToken").asText();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private JsonNode get(String token, String url, Object... vars) {
        ResponseEntity<JsonNode> r = http.exchange(url, HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class, vars);
        assertThat(r.getStatusCode()).as("GET " + url).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private ResponseEntity<JsonNode> patchStatus(String token, UUID alertId, String body) {
        return http.exchange("/api/v1/alerts/{id}/status", HttpMethod.PATCH,
                new HttpEntity<>(body, bearer(token)), JsonNode.class, alertId);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void login_returns_jwt_with_roles_and_rejects_bad_credentials() {
        ResponseEntity<JsonNode> ok = http.postForEntity("/api/v1/auth/login",
                Map.of("username", "supervisor", "password", "supervisor123"), JsonNode.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(ok.getBody().get("username").asText()).isEqualTo("supervisor");
        assertThat(ok.getBody().get("roles")).hasSize(2);
        assertThat(ok.getBody().get("accessToken").asText()).contains(".");

        ResponseEntity<JsonNode> ko = http.postForEntity("/api/v1/auth/login",
                Map.of("username", "analyst", "password", "mauvais"), JsonNode.class);
        assertThat(ko.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ko.getBody().get("title").asText()).isEqualTo("Authentification échouée");
    }

    @Test
    void api_requires_a_valid_token() {
        ResponseEntity<String> anonymous = http.getForEntity("/api/v1/alerts", String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> forged = http.exchange("/api/v1/alerts", HttpMethod.GET,
                new HttpEntity<>(bearer("not.a.jwt")), String.class);
        assertThat(forged.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void review_workflow_end_to_end_with_roles() {
        UUID tx = insertTransaction("TRANSFER", new BigDecimal("400000.00"), "C_ORIG", "C_MULE",
                true, true, new BigDecimal("95"), T0);
        UUID alertId = insertAlert(tx, "ACCOUNT_DRAIN", "CRITICAL", new BigDecimal("85"), "OPEN", T0);
        insertAlert(tx, "HIGH_AMOUNT", "HIGH", new BigDecimal("60"), "OPEN", T0);

        String analyst = login("analyst", "analyst123");
        String supervisor = login("supervisor", "supervisor123");

        // Liste filtrée
        JsonNode page = get(analyst, "/api/v1/alerts?status=OPEN&severity=CRITICAL");
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.at("/content/0/alert/id").asText()).isEqualTo(alertId.toString());
        assertThat(page.at("/content/0/transaction/nameDest").asText()).isEqualTo("C_MULE");

        // L'analyste passe l'alerte en revue : le relecteur tracé est son identifiant
        ResponseEntity<JsonNode> reviewed = patchStatus(analyst, alertId,
                "{\"status\":\"UNDER_REVIEW\",\"comment\":\"en cours\"}");
        assertThat(reviewed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reviewed.getBody().get("status").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(reviewed.getBody().get("reviewedBy").asText()).isEqualTo("analyst");

        // L'analyste ne peut pas bloquer : 403, rien ne change
        ResponseEntity<JsonNode> refusedBlock = patchStatus(analyst, alertId, "{\"status\":\"BLOCKED\"}");
        assertThat(refusedBlock.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusedBlock.getBody().get("title").asText()).isEqualTo("Habilitation insuffisante");
        assertThat(get(analyst, "/api/v1/alerts/{id}", alertId).at("/alert/status").asText()).isEqualTo("UNDER_REVIEW");

        // Le superviseur bloque
        ResponseEntity<JsonNode> blocked = patchStatus(supervisor, alertId,
                "{\"status\":\"BLOCKED\",\"comment\":\"compte bloqué\"}");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blocked.getBody().get("reviewedBy").asText()).isEqualTo("supervisor");
        assertThat(blocked.getBody().get("allowedTransitions")).isEmpty();

        // Transition interdite depuis un état terminal -> 409 Problem Details
        ResponseEntity<JsonNode> refused = patchStatus(supervisor, alertId, "{\"status\":\"OPEN\"}");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("currentStatus").asText()).isEqualTo("BLOCKED");

        // Historique persisté dans l'ordre, avec les auteurs
        JsonNode history = get(analyst, "/api/v1/alerts/{id}/history", alertId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("newStatus").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(history.get(0).get("changedBy").asText()).isEqualTo("analyst");
        assertThat(history.get(1).get("newStatus").asText()).isEqualTo("BLOCKED");
        assertThat(history.get(1).get("changedBy").asText()).isEqualTo("supervisor");

        // Détail de la transaction avec ses alertes
        assertThat(get(analyst, "/api/v1/transactions/{id}", tx).get("alerts")).hasSize(2);

        // Statistiques
        JsonNode stats = get(analyst, "/api/v1/stats/overview");
        assertThat(stats.get("totalTransactions").asLong()).isEqualTo(1);
        assertThat(stats.get("truePositives").asLong()).isEqualTo(1);
        assertThat(stats.get("recall").asDouble()).isEqualTo(1.0);
        assertThat(stats.at("/alertsByStatus/BLOCKED").asLong()).isEqualTo(1);
        assertThat(stats.at("/alertsByStatus/OPEN").asLong()).isEqualTo(1);
    }

    @Test
    void model_and_rule_statistics_endpoints() {
        UUID tx = insertTransaction("TRANSFER", new BigDecimal("900.00"), "C_O", "C_D", true, true, new BigDecimal("97"), T0);
        jdbc.update("UPDATE fraud.transactions SET ml_score = 97.00, model_version = 'v_test' WHERE transaction_id = ?", tx);
        insertAlert(tx, "ML_HIGH_RISK", "HIGH", new BigDecimal("75"), "CONFIRMED_FRAUD", T0);
        String analyst = login("analyst", "analyst123");

        JsonNode models = get(analyst, "/api/v1/stats/models");
        assertThat(models).hasSize(1);
        assertThat(models.get(0).get("modelVersion").asText()).isEqualTo("v_test");
        assertThat(models.get(0).get("mlAlertsConfirmed").asLong()).isEqualTo(1);
        assertThat(models.get(0).get("precisionVsAnalysts").asDouble()).isEqualTo(1.0);
        assertThat(models.get(0).get("avgMlScore").asDouble()).isEqualTo(97.0);

        JsonNode rules = get(analyst, "/api/v1/stats/rules");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).get("ruleName").asText()).isEqualTo("ML_HIGH_RISK");
        assertThat(rules.get(0).get("confirmed").asLong()).isEqualTo(1);
    }

    @Test
    void unknown_alert_returns_404_problem_detail() {
        String analyst = login("analyst", "analyst123");
        ResponseEntity<JsonNode> response = http.exchange("/api/v1/alerts/{id}", HttpMethod.GET,
                new HttpEntity<>(bearer(analyst)), JsonNode.class, UUID.randomUUID());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Ressource introuvable");
    }

    @Test
    void public_endpoints_need_no_token() {
        JsonNode health = http.getForObject("/actuator/health", JsonNode.class);
        assertThat(health.get("status").asText()).isEqualTo("UP");
        assertThat(health.at("/components/db/status").asText()).isEqualTo("UP");

        ResponseEntity<String> docs = http.getForEntity("/v3/api-docs", String.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody()).contains("bearerAuth");
    }
}
