package com.example.demo.savings.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SimulateRequest(
        @Valid @NotNull ProfileRequest profile,
        List<String> productIds
) {
}

