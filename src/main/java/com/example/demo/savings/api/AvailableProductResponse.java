package com.example.demo.savings.api;

public record AvailableProductResponse(
        String productId,
        String productName,
        double expectedRate,
        long requiredCardResource,
        String status,
        String warningMessage
) {
}
