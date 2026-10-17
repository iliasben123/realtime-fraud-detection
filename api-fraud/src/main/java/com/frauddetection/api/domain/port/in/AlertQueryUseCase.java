package com.frauddetection.api.domain.port.in;

import com.frauddetection.api.domain.model.AlertDetail;
import com.frauddetection.api.domain.model.AlertFilter;
import com.frauddetection.api.domain.model.AlertStatusChange;
import com.frauddetection.api.domain.model.PageQuery;
import com.frauddetection.api.domain.model.PageResult;

import java.util.List;
import java.util.UUID;

/** Port entrant : consultation des alertes. */
public interface AlertQueryUseCase {

    PageResult<AlertDetail> search(AlertFilter filter, PageQuery page);

    AlertDetail getById(UUID alertId);

    List<AlertStatusChange> history(UUID alertId);
}
