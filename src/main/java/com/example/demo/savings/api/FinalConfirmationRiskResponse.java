package com.example.demo.savings.api;

public record FinalConfirmationRiskResponse(
        String severity,
        String productId,
        String conditionId,
        String message
) {
}
