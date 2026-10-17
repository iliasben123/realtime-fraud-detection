package com.frauddetection.api.application;

import com.frauddetection.api.domain.model.ModelVersionStatistics;
import com.frauddetection.api.domain.model.RuleFeedback;
import com.frauddetection.api.domain.port.in.ModelStatisticsUseCase;
import com.frauddetection.api.domain.port.out.ModelStatisticsRepository;

import java.util.Comparator;
import java.util.List;

public class ModelStatisticsService implements ModelStatisticsUseCase {

    private final ModelStatisticsRepository repository;

    public ModelStatisticsService(ModelStatisticsRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ModelVersionStatistics> byModelVersion() {
        return repository.byModelVersion().stream()
                .sorted(Comparator.comparing(ModelVersionStatistics::modelVersion).reversed())
                .toList();
    }

    @Override
    public List<RuleFeedback> ruleFeedback() {
        return repository.ruleFeedback().stream()
                .sorted(Comparator.comparingLong(RuleFeedback::alerts).reversed()
                        .thenComparing(RuleFeedback::ruleName))
                .toList();
    }
}
