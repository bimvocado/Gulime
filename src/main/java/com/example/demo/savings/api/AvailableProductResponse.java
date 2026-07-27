package com.example.demo.savings.api;

public record AvailableProductResponse(
        String productId,
        String productName,
        int termMonths,
        double expectedRate,
        long requiredCardResource,
        String status,
        String warningMessage
) {
}
