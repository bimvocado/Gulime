package com.example.demo.savings.domain;

public record AllocationSlot(
        AllocationType allocationType,
        long amount,
        int termMonths,
        int startMonth
) {
    public AllocationSlot(AllocationType allocationType, long amount, int termMonths) {
        this(allocationType, amount, termMonths, 0);
    }

    public AllocationSlot {
        if (amount <= 0L || termMonths <= 0 || startMonth < 0) {
            throw new IllegalArgumentException("유효하지 않은 배분 슬롯입니다.");
        }
    }
}
