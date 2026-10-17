package com.frauddetection.api.adapter.in.web.dto;

public record RuleFeedbackResponse(
        String ruleName,
        long alerts,
        long onLabeledFraud,
        long confirmed,
        long falsePositive,
        long pending,
        double precisionVsLabel,
        double precisionVsAnalysts,
        double reviewRate
) {
}
