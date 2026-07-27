package com.example.demo.savings.api;

import java.util.List;

public record ProductSimulationResponse(
        String productId,
        String productName,
        String bankName,
        double baseRate,
        double advertisedMaxRate,
        double expectedRate,
        RangeResponse expectedRateConfidenceInterval,
        double profileAchievableMaxRate,
        List<ConditionDiagnosticResponse> conditionEvaluations,
        List<ExcludedConditionResponse> excludedConditions,
        SensitivityResponse sensitivityAnalysis
) {
    public ProductSimulationResponse {
        conditionEvaluations = List.copyOf(conditionEvaluations);
        excludedConditions = List.copyOf(excludedConditions);
    }
}
