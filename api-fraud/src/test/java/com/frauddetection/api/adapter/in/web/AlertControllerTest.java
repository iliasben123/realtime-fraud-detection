package com.frauddetection.api.adapter.in.web;

import com.frauddetection.api.config.UseCaseConfiguration;
import com.frauddetection.api.domain.exception.AlertNotFoundException;
import com.frauddetection.api.domain.exception.InvalidStatusTransitionException;
import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.AlertDetail;
import com.frauddetection.api.domain.model.AlertFilter;
import com.frauddetection.api.domain.model.AlertSeverity;
import com.frauddetection.api.domain.model.AlertStatus;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;
import com.frauddetection.api.domain.model.Transaction;
import com.frauddetection.api.domain.model.TransactionType;
import com.frauddetection.api.domain.port.in.AlertQueryUseCase;
import com.frauddetection.api.domain.port.in.AlertStatusUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AlertController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = UseCaseConfiguration.class))
class AlertControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AlertQueryUseCase queries;
    @MockitoBean AlertStatusUseCase statusUseCase;

    private static final Instant T = Instant.parse("2026-03-01T10:00:00Z");

    private static AlertDetail detail(AlertStatus status) {
        UUID txId = UUID.randomUUID();
        Alert alert = new Alert(UUID.randomUUID(), txId, "HIGH_AMOUNT", "Montant élevé", AlertSeverity.HIGH,
                new BigDecimal("60.00"), status, T, T, null, null);
        Transaction tx = new Transaction(txId, 12, T, T, TransactionType.TRANSFER, new BigDecimal("250000.00"),
                "C1", new BigDecimal("300000.00"), new BigDecimal("50000.00"), "C2",
                BigDecimal.ZERO, BigDecimal.ZERO, true, false, new BigDecimal("60.00"), true,
                new BigDecimal("97.50"), "v20260301_100000");
        return new AlertDetail(alert, tx);
    }

    @Test
    void search_returns_page_and_forwards_filters() throws Exception {
        AlertDetail d = detail(AlertStatus.OPEN);
        when(queries.search(any(), any())).thenReturn(new PageResult<>(List.of(d), 0, 20, 1));

        mvc.perform(get("/api/v1/alerts")
                        .param("status", "OPEN")
                        .param("severity", "HIGH")
                        .param("minAmount", "100000")
                        .param("sortBy", "riskScore")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].alert.ruleName").value("HIGH_AMOUNT"))
                .andExpect(jsonPath("$.content[0].alert.allowedTransitions").isArray())
                .andExpect(jsonPath("$.content[0].transaction.amount").value(250000.00))
                .andExpect(jsonPath("$.content[0].transaction.mlScore").value(97.50))
                .andExpect(jsonPath("$.content[0].transaction.modelVersion").value("v20260301_100000"));

        ArgumentCaptor<AlertFilter> filter = ArgumentCaptor.forClass(AlertFilter.class);
        ArgumentCaptor<PageQuery> page = ArgumentCaptor.forClass(PageQuery.class);
        verify(queries).search(filter.capture(), page.capture());
        assertThat(filter.getValue().status()).isEqualTo(AlertStatus.OPEN);
        assertThat(filter.getValue().severity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(filter.getValue().minAmount()).isEqualByComparingTo("100000");
        assertThat(page.getValue().sortBy()).isEqualTo("riskScore");
        assertThat(page.getValue().direction()).isEqualTo(PageQuery.SortDirection.ASC);
    }

    @Test
    void search_with_unknown_sort_field_is_bad_request() throws Exception {
        mvc.perform(get("/api/v1/alerts").param("sortBy", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requête invalide"));
    }

    @Test
    void get_unknown_alert_is_not_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(queries.getById(id)).thenThrow(new AlertNotFoundException(id));

        mvc.perform(get("/api/v1/alerts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Alerte introuvable : " + id));
    }

    @Test
    void patch_status_returns_updated_alert() throws Exception {
        AlertDetail d = detail(AlertStatus.OPEN);
        Alert confirmed = d.alert().transitionTo(AlertStatus.CONFIRMED_FRAUD, "analyst", "ok", T);
        when(statusUseCase.changeStatus(eq(d.alert().id()), eq(AlertStatus.CONFIRMED_FRAUD), eq("analyst"), eq("ok")))
                .thenReturn(confirmed);

        mvc.perform(patch("/api/v1/alerts/{id}/status", d.alert().id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED_FRAUD","reviewedBy":"analyst","comment":"ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_FRAUD"))
                .andExpect(jsonPath("$.reviewedBy").value("analyst"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("BLOCKED"));
    }

    @Test
    void patch_forbidden_transition_is_conflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(statusUseCase.changeStatus(eq(id), eq(AlertStatus.OPEN), any(), any()))
                .thenThrow(new InvalidStatusTransitionException(id, AlertStatus.BLOCKED, AlertStatus.OPEN));

        mvc.perform(patch("/api/v1/alerts/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.requestedStatus").value("OPEN"))
                .andExpect(jsonPath("$.allowedTransitions").isEmpty());
    }

    @Test
    void patch_without_status_fails_validation() throws Exception {
        mvc.perform(patch("/api/v1/alerts/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewedBy\":\"analyst\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }
}
