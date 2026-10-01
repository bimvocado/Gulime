package com.example.demo.savings.api;

import java.util.List;

public record RoadmapResponse(
        InitialAllocationResponse initialAllocation,
        RoadmapSummaryResponse summary,
        List<RoadmapMilestoneResponse> milestones,
        List<FinalConfirmationRiskResponse> finalConfirmationRisks,
        String reinvestNote
) {
    public RoadmapResponse {
        milestones = List.copyOf(milestones);
        finalConfirmationRisks = List.copyOf(finalConfirmationRisks);
    }
}
