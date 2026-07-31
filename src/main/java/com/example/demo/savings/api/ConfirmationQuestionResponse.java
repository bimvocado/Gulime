package com.example.demo.savings.api;

public record ConfirmationQuestionResponse(
        String conditionId,
        String question,
        double rateImpact
) {
}
