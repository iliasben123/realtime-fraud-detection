package com.frauddetection.api.domain.port.in;

import com.frauddetection.api.domain.model.Alert;
import com.frauddetection.api.domain.model.AlertStatus;

import java.util.UUID;

/** Port entrant : gestion du cycle de vie d'une alerte (valider, bloquer, classer sans suite...). */
public interface AlertStatusUseCase {

    /**
     * Change le statut d'une alerte en respectant les transitions autorisées
     * et trace le changement dans l'historique.
     */
    Alert changeStatus(UUID alertId, AlertStatus target, String reviewedBy, String comment);
}
