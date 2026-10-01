package com.example.demo.savings.domain;

public record SelectedAllocation(
        int slotIndex,
        String productId,
        long amount,
        int startMonth
) {
    public SelectedAllocation(int slotIndex, String productId, long amount) {
        this(slotIndex, productId, amount, 0);
    }
}

