package com.example.demo.savings.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

public record OptionsRequest(
        @Valid @NotNull ProfileRequest profile,
        @DecimalMin("0.0") @DecimalMax("1.0") double riskTolerance
) {
}
