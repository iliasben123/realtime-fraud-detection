package com.frauddetection.api.adapter.in.web;

import com.frauddetection.api.adapter.in.web.dto.StatisticsResponse;
import com.frauddetection.api.domain.port.in.StatisticsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Statistiques", description = "Indicateurs globaux de détection")
class StatisticsController {

    private final StatisticsUseCase statistics;

    StatisticsController(StatisticsUseCase statistics) {
        this.statistics = statistics;
    }

    @GetMapping("/overview")
    @Operation(summary = "Vue d'ensemble",
            description = "Volumes, précision / rappel des règles par rapport au label PaySim, répartition des alertes.")
    StatisticsResponse overview() {
        return WebMapper.toResponse(statistics.overview());
    }
}
