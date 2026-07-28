package com.example.demo.savings.service;

import com.example.demo.savings.domain.ProductCondition;
import com.example.demo.savings.domain.SavingsProduct;

import java.util.List;

public record ProductEvaluation(
        SavingsProduct product,
        double expectedRate,
        double lowerRate,
        double upperRate,
        double profileAchievableMaxRate,
        double variancePlusRate,
        double varianceMinusRate,
        double rateRisk,
        boolean hardRequirementsSatisfied,
        List<ConditionEvaluation> conditions,
        List<ProductCondition> parseExcludedConditions,
        ResourceDemand resourceDemand
) {
    public ProductEvaluation {
        conditions = List.copyOf(conditions);
        parseExcludedConditions = List.copyOf(parseExcludedConditions);
    }
}

