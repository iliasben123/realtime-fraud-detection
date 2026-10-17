package com.frauddetection.api.domain.port.in;

import com.frauddetection.api.domain.model.AlertStatistics;

/** Port entrant : indicateurs globaux. */
public interface StatisticsUseCase {

    AlertStatistics overview();
}
