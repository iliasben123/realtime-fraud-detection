package com.frauddetection.api.adapter.in.web;

import com.frauddetection.api.adapter.in.web.dto.ModelVersionStatisticsResponse;
import com.frauddetection.api.adapter.in.web.dto.RuleFeedbackResponse;
import com.frauddetection.api.adapter.in.web.dto.StatisticsResponse;
import com.frauddetection.api.domain.port.in.ModelStatisticsUseCase;
import com.frauddetection.api.domain.port.in.StatisticsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Statistiques", description = "Indicateurs globaux et suivi de la qualité de détection")
class StatisticsController {

    private final StatisticsUseCase statistics;
    private final ModelStatisticsUseCase modelStatistics;

    StatisticsController(StatisticsUseCase statistics, ModelStatisticsUseCase modelStatistics) {
        this.statistics = statistics;
        this.modelStatistics = modelStatistics;
    }

    @GetMapping("/overview")
    @Operation(summary = "Vue d'ensemble",
            description = "Volumes, précision / rappel des règles par rapport au label PaySim, répartition des alertes.")
    StatisticsResponse overview() {
        return WebMapper.toResponse(statistics.overview());
    }

    @GetMapping("/models")
    @Operation(summary = "Qualité par version de modèle MLlib",
            description = "Pour chaque version ayant scoré des transactions : précision / rappel contre le label, "
                    + "et précision « terrain » calculée sur les décisions des analystes (CONFIRMED_FRAUD / BLOCKED "
                    + "vs FALSE_POSITIVE) portant sur les alertes ML_HIGH_RISK.")
    List<ModelVersionStatisticsResponse> models() {
        return modelStatistics.byModelVersion().stream().map(WebMapper::toResponse).toList();
    }

    @GetMapping("/rules")
    @Operation(summary = "Retour des analystes par règle",
            description = "Pour chaque règle (métier ou ML) : alertes émises, part sur fraude labellisée, "
                    + "décisions des analystes et taux de revue.")
    List<RuleFeedbackResponse> rules() {
        return modelStatistics.ruleFeedback().stream().map(WebMapper::toResponse).toList();
    }
}
