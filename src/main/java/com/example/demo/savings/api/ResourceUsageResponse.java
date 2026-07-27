package com.example.demo.savings.api;

public record ResourceUsageResponse(
        long cardBudget,
        boolean cardBudgetExceeded,
        int salaryTransfer,
        boolean salaryTransferExceeded,
        long cashBalance,
        boolean cashBalanceExceeded
) {
}

