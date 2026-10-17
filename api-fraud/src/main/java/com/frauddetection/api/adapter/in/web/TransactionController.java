package com.frauddetection.api.adapter.in.web;

import com.frauddetection.api.adapter.in.web.dto.PageResponse;
import com.frauddetection.api.adapter.in.web.dto.TransactionDetailResponse;
import com.frauddetection.api.adapter.in.web.dto.TransactionResponse;
import com.frauddetection.api.domain.model.TransactionFilter;
import com.frauddetection.api.domain.model.TransactionType;
import com.frauddetection.api.domain.port.in.TransactionQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Consultation des transactions analysées par le moteur Spark")
class TransactionController {

    private final TransactionQueryUseCase queries;

    TransactionController(TransactionQueryUseCase queries) {
        this.queries = queries;
    }

    @GetMapping
    @Operation(summary = "Recherche paginée de transactions",
            description = "Ex. : ?suspicious=true&minAmount=100000 pour les transactions suspectes de gros montant.")
    PageResponse<TransactionResponse> search(
            @RequestParam(required = false) Boolean suspicious,
            @RequestParam(required = false) @Parameter(description = "Label PaySim isFraud") Boolean fraud,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @Parameter(description = "Compte émetteur ou destinataire") String account,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) BigDecimal minRiskScore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "eventTime") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        TransactionFilter filter = new TransactionFilter(
                suspicious, fraud, type, account, minAmount, maxAmount, minRiskScore, from, to);
        var pageQuery = WebMapper.toPageQuery(page, size, sortBy, direction, WebMapper.TRANSACTION_SORT_FIELDS);
        return PageResponse.from(queries.search(filter, pageQuery), WebMapper::toResponse);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Détail d'une transaction et des alertes associées")
    TransactionDetailResponse getById(@PathVariable UUID transactionId) {
        var transaction = queries.getById(transactionId);
        var alerts = queries.alertsOf(transactionId).stream().map(WebMapper::toResponse).toList();
        return new TransactionDetailResponse(WebMapper.toResponse(transaction), alerts);
    }
}
