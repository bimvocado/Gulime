package com.example.demo.savings.api;

public record InitialSavingResponse(
        int startMonth,
        String productId,
        String productName,
        long monthlyAmount,
        int termMonths
) {
}
