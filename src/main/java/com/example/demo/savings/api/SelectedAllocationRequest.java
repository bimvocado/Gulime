package com.example.demo.savings.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SelectedAllocationRequest(
        @Min(0) int slotIndex,
        @NotBlank String productId,
        @Positive long amount
) {
}

