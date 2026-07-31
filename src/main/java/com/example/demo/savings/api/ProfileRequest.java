package com.example.demo.savings.api;

import com.example.demo.savings.domain.EmploymentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record ProfileRequest(
        @NotNull EmploymentType employment,
        boolean salaryTransferable,
        @PositiveOrZero long lumpSum,
        @PositiveOrZero long emergencyFund,
        @PositiveOrZero long monthlySaving,
        @Min(1) int targetMonths,
        @NotEmpty @Size(min = 6, max = 6)
        List<@NotNull @PositiveOrZero Long> cardSpend6m,
        @PositiveOrZero long cardBudgetCap,
        @NotNull List<@NotNull String> existingBanks,
        Map<String, Boolean> conditionAnswers
) {
    public ProfileRequest {
        conditionAnswers = conditionAnswers == null
                ? Map.of()
                : Map.copyOf(conditionAnswers);
    }

    public ProfileRequest(
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
        this(
                employment,
                salaryTransferable,
                lumpSum,
                emergencyFund,
                monthlySaving,
                targetMonths,
                cardSpend6m,
                cardBudgetCap,
                existingBanks,
                Map.of()
        );
    }
}
