package com.example.demo.savings.service;

import java.util.Map;

public record ResourceDemand(
        long cardBudget,
        int salaryTransfer,
        Map<String, Integer> firstTradeByBank,
        long cashBalance
) {
    public ResourceDemand {
        firstTradeByBank = Map.copyOf(firstTradeByBank);
    }
}

