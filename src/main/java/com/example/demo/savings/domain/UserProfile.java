package com.example.demo.savings.domain;

import java.util.Collections;
import java.util.List;

public record UserProfile(
        EmploymentType employment,
        boolean salaryTransferable,
        long lumpSum,
        long emergencyFund,
        long monthlySaving,
        int targetMonths,
        List<Long> cardSpend6m,
        long cardBudgetCap,
        List<String> existingBanks
) {
    // Compact Constructor (검증 및 방어적 복사)
    public UserProfile {
        // 1. targetMonths 기본값 보장 (0 이하로 들어오면 12로 기본 설정)
        if (targetMonths <= 0) {
            targetMonths = 12;
        }

        // 2. cardSpend6m Null Safety & 불변 리스트 변환
        cardSpend6m = (cardSpend6m == null)
                ? List.of()
                : List.copyOf(cardSpend6m);

        // 3. existingBanks Null Safety & 불변 리스트 변환
        existingBanks = (existingBanks == null)
                ? List.of()
                : List.copyOf(existingBanks);
    }
}