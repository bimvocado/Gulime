package com.example.demo.savings.api;

import java.util.List;

public record PortfolioResponse(
        String optionType,
        long expectedFinalAmount,
        long expectedTotalReturn,
        double weightedExpectedRate,
        String riskLevel,
        double riskScore,
        boolean feasible,
        ResourceUsageResponse resourceUsage,
        List<AllocationResponse> allocations
) {
    public PortfolioResponse {
        allocations = List.copyOf(allocations);
    }
}
