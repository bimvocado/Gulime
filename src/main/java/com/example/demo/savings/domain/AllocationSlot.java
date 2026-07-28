package com.example.demo.savings.domain;

public record AllocationSlot(
        AllocationType allocationType,
        long amount,
        int termMonths
) {
}
