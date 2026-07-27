package com.example.demo.savings.service;

import java.util.List;
import java.util.Map;

record PortfolioCandidate(
        List<PortfolioAllocation> allocations,
        long principal,
        long lumpSumPrincipal,
        double expectedReturn,
        double returnRisk,
        long cardBudgetUsed,
        int salaryTransferUsed,
        long cashBalanceUsed,
        Map<String, Integer> firstTradeUsed
) {
    PortfolioCandidate {
        allocations = List.copyOf(allocations);
        firstTradeUsed = Map.copyOf(firstTradeUsed);
    }

    double expectedFinalAmount() {
        return principal + expectedReturn;
    }
}
