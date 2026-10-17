package com.frauddetection.api.domain.port.in;

import com.frauddetection.api.domain.model.ModelVersionStatistics;
import com.frauddetection.api.domain.model.RuleFeedback;

import java.util.List;

/** Port entrant : suivi de la qualité de détection (versions de modèle, règles) croisée avec les analystes. */
public interface ModelStatisticsUseCase {

    /** Une entrée par version de modèle ayant scoré des transactions, la plus récente d'abord. */
    List<ModelVersionStatistics> byModelVersion();

    /** Une entrée par règle ayant émis des alertes, la plus active d'abord. */
    List<RuleFeedback> ruleFeedback();
}
