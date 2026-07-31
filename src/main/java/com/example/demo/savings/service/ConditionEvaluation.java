package com.example.demo.savings.service;

import com.example.demo.savings.domain.ProductCondition;

public record ConditionEvaluation(
        ProductCondition condition,
        boolean achievable,
        double probability,
        double lowerProbability,
        double upperProbability,
        double variancePlusProbability,
        double varianceMinusProbability,
        String reason,
        boolean selected,
        boolean confirmationRequired
) {
    public ConditionEvaluation withSelected(boolean selected) {
        return new ConditionEvaluation(
                condition,
                achievable,
                probability,
                lowerProbability,
                upperProbability,
                variancePlusProbability,
                varianceMinusProbability,
                reason,
                selected,
                confirmationRequired
        );
    }
}

