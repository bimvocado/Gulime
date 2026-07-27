package com.example.demo.savings.domain;

public record SelectedAllocation(
        int slotIndex,
        String productId,
        long amount
) {
}

