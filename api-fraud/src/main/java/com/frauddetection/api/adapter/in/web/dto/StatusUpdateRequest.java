package com.frauddetection.api.adapter.in.web.dto;

import com.frauddetection.api.domain.model.AlertStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Le relecteur n'est pas dans le corps de la requête : c'est l'utilisateur authentifié. */
public record StatusUpdateRequest(
        @NotNull
        @Schema(description = "Nouveau statut (BLOCKED réservé au rôle SUPERVISOR)", example = "CONFIRMED_FRAUD")
        AlertStatus status,

        @Size(max = 1000)
        @Schema(description = "Commentaire de revue", example = "Client contacté, opération non reconnue")
        String comment
) {
}
