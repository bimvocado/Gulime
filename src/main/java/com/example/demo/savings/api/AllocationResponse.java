package com.example.demo.savings.api;

public record AllocationResponse(
        int slotIndex,
        String allocationType,
        String productId,
        String productName,
        String bankName,
        long amount,
        long monthlyAmount,
        int termMonths,
        int startMonth,
        int maturityMonth,
        double expectedRate,
        long requiredCardResource,
        String status,
        String warningMessage
) {
}
