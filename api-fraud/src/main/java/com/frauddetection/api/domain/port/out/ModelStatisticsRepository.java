package com.frauddetection.api.domain.port.out;

import com.frauddetection.api.domain.model.ModelVersionStatistics;
import com.frauddetection.api.domain.model.RuleFeedback;

import java.util.List;

/** Port sortant : agrégations croisant transactions, alertes et statuts. */
public interface ModelStatisticsRepository {

    List<ModelVersionStatistics> byModelVersion();

    List<RuleFeedback> ruleFeedback();
}
