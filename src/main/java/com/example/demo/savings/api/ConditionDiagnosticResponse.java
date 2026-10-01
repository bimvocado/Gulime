package com.example.demo.savings.api;

import com.example.demo.savings.domain.ResourceType;

public record ConditionDiagnosticResponse(
        String conditionId,
        String conditionName,
        String sourceText,
        double rateBonus,
        boolean achievable,
        double achievementProbability,
        RangeResponse confidenceInterval,
        RangeResponse sensitivityProbabilityRange,
        String status,
        String reason,
        ResourceType resource
) {
}
