package com.frauddetection.api.adapter.in.web.dto;

import java.util.List;

/** Transaction accompagnée des alertes qu'elle a déclenchées. */
public record TransactionDetailResponse(TransactionResponse transaction, List<AlertResponse> alerts) {
}
