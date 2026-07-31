package com.example.demo.savings.api;

import java.util.List;

public record ProductSimulationResponse(
        String productId,
        String productName,
        String bankName,
        int termMonths,
        double baseRate,
        double advertisedMaxRate,
        double expectedRate,
        RangeResponse expectedRateConfidenceInterval,
        double profileAchievableMaxRate,
        List<ConditionDiagnosticResponse> conditionEvaluations,
        List<ExcludedConditionResponse> excludedConditions,
        List<ConfirmationQuestionResponse> confirmationQuestions,
        SensitivityResponse sensitivityAnalysis
) {
    public ProductSimulationResponse {
        conditionEvaluations = List.copyOf(conditionEvaluations);
        excludedConditions = List.copyOf(excludedConditions);
        confirmationQuestions = List.copyOf(confirmationQuestions);
    }
}
