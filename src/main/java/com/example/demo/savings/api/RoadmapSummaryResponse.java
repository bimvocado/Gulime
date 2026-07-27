package com.example.demo.savings.api;

public record RoadmapSummaryResponse(
        long totalPrincipal,
        long emergencyFund,
        long monthlySaving,
        long expectedTotalReturn,
        double effectiveRate
) {
}

