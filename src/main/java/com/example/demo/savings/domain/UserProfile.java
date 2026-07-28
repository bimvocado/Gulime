package com.example.demo.savings.domain;

import java.util.List;

public record UserProfile(
        EmploymentType employment,
        boolean salaryTransferable,
        long lumpSum,
        long emergencyFund,
        long monthlySaving,
        List<Long> cardSpend6m,
        long cardBudgetCap,
        List<String> existingBanks
) {
    public UserProfile {
        cardSpend6m = List.copyOf(cardSpend6m);
        existingBanks = List.copyOf(existingBanks);
    }
}

