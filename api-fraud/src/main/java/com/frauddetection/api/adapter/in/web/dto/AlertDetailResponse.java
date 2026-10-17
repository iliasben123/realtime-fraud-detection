package com.frauddetection.api.adapter.in.web.dto;

/** Alerte accompagnée de la transaction qui l'a déclenchée. */
public record AlertDetailResponse(AlertResponse alert, TransactionResponse transaction) {
}
