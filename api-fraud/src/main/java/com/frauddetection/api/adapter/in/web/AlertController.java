package com.frauddetection.api.adapter.in.web;

import com.frauddetection.api.adapter.in.web.dto.AlertDetailResponse;
import com.frauddetection.api.adapter.in.web.dto.AlertResponse;
import com.frauddetection.api.adapter.in.web.dto.PageResponse;
import com.frauddetection.api.adapter.in.web.dto.StatusHistoryResponse;
import com.frauddetection.api.adapter.in.web.dto.StatusUpdateRequest;
import com.frauddetection.api.adapter.in.web.security.ReviewerMapper;
import com.frauddetection.api.domain.model.AlertFilter;
import com.frauddetection.api.domain.model.AlertSeverity;
import com.frauddetection.api.domain.model.AlertStatus;
import com.frauddetection.api.domain.port.in.AlertQueryUseCase;
import com.frauddetection.api.domain.port.in.AlertStatusUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alertes", description = "Consultation et gestion du cycle de vie des alertes de fraude")
class AlertController {

    private final AlertQueryUseCase queries;
    private final AlertStatusUseCase statusUseCase;

    AlertController(AlertQueryUseCase queries, AlertStatusUseCase statusUseCase) {
        this.queries = queries;
        this.statusUseCase = statusUseCase;
    }

    @GetMapping
    @Operation(summary = "Recherche paginée d'alertes",
            description = "Filtres combinables : statut, sévérité, règle, compte, montant, score, période.")
    PageResponse<AlertDetailResponse> search(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) @Parameter(description = "Compte émetteur ou destinataire") String account,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal minRiskScore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        AlertFilter filter = new AlertFilter(status, severity, ruleName, account, minAmount, minRiskScore, from, to);
        var pageQuery = WebMapper.toPageQuery(page, size, sortBy, direction, WebMapper.ALERT_SORT_FIELDS);
        return PageResponse.from(queries.search(filter, pageQuery), WebMapper::toResponse);
    }

    @GetMapping("/{alertId}")
    @Operation(summary = "Détail d'une alerte et de sa transaction")
    AlertDetailResponse getById(@PathVariable UUID alertId) {
        return WebMapper.toResponse(queries.getById(alertId));
    }

    @GetMapping("/{alertId}/history")
    @Operation(summary = "Historique des changements de statut")
    List<StatusHistoryResponse> history(@PathVariable UUID alertId) {
        return queries.history(alertId).stream().map(WebMapper::toResponse).toList();
    }

    @PatchMapping("/{alertId}/status")
    @Operation(summary = "Changer le statut d'une alerte",
            description = "Transitions autorisées : OPEN -> UNDER_REVIEW | CONFIRMED_FRAUD | FALSE_POSITIVE | BLOCKED ; "
                    + "UNDER_REVIEW -> CONFIRMED_FRAUD | FALSE_POSITIVE | BLOCKED ; CONFIRMED_FRAUD -> BLOCKED. "
                    + "Une transition interdite renvoie 409. BLOCKED exige le rôle SUPERVISOR (403 sinon). "
                    + "Le relecteur tracé est l'utilisateur authentifié.")
    AlertResponse changeStatus(@PathVariable UUID alertId, @Valid @RequestBody StatusUpdateRequest request,
                               Authentication authentication) {
        return WebMapper.toResponse(statusUseCase.changeStatus(
                alertId, request.status(), ReviewerMapper.from(authentication), request.comment()));
    }
}
