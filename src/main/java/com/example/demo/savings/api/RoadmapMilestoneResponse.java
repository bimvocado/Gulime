package com.example.demo.savings.api;

public record RoadmapMilestoneResponse(
        int month,
        String eventType,
        String productId,
        String productName,
        long amount,
        String action,
        boolean recalculationRequired
) {
}
