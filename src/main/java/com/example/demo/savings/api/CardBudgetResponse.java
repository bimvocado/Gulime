package com.example.demo.savings.api;

public record CardBudgetResponse(
        long mean,
        long std,
        String distribution
) {
}

