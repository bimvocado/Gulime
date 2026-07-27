package com.example.demo.savings.api;

import com.example.demo.savings.domain.EmploymentType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfileRequest(
        @NotNull EmploymentType employment,
        boolean salaryTransferable,
        @PositiveOrZero long lumpSum,
        @PositiveOrZero long emergencyFund,
        @PositiveOrZero long monthlySaving,
        @NotEmpty @Size(min = 6, max = 6)
        List<@NotNull @PositiveOrZero Long> cardSpend6m,
        @PositiveOrZero long cardBudgetCap,
        @NotNull List<@NotNull String> existingBanks
) {
}

