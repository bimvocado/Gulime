package com.example.demo.savings.api;

import java.util.List;

public record InitialAllocationResponse(
        long parkingFund,
        List<InitialDepositResponse> deposits,
        List<InitialSavingResponse> savings
) {
    public InitialAllocationResponse {
        deposits = List.copyOf(deposits);
        savings = List.copyOf(savings);
    }
}
