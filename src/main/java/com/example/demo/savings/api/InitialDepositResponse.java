package com.example.demo.savings.api;

public record InitialDepositResponse(
        int month,
        String productId,
        String productName,
        long amount,
        int termMonths
) {
}
