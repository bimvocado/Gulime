package com.example.demo.savings.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoadmapRequest(
        @Valid @NotNull ProfileRequest profile,
        @NotEmpty List<@Valid SelectedAllocationRequest> selectedAllocations
) {
}

