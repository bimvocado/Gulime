package com.example.demo.savings.api;

import java.util.Map;

public record ResourceBudgetResponse(
        long cardBudget,
        int salaryTransfer,
        Map<String, Integer> firstTrade,
        long cashBalance
) {
    public ResourceBudgetResponse {
        firstTrade = Map.copyOf(firstTrade);
    }
}

