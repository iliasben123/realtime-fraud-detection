package com.frauddetection.api.adapter.in.web.dto;

import com.frauddetection.api.domain.model.AlertStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull
        @Schema(description = "Nouveau statut", example = "CONFIRMED_FRAUD")
        AlertStatus status,

        @Size(max = 64)
        @Schema(description = "Identifiant de l'analyste", example = "analyst.jdoe")
        String reviewedBy,

        @Size(max = 1000)
        @Schema(description = "Commentaire de revue", example = "Client contacté, opération non reconnue")
        String comment
) {
}
