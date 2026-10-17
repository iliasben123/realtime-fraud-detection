package com.frauddetection.api.domain.model;

/** Alerte enrichie de la transaction qui l'a déclenchée. */
public record AlertDetail(Alert alert, Transaction transaction) {
}
