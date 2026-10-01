package com.example.demo.savings.api;

public record SensitivityResponse(
        int volatilityChangePercent,
        RangeResponse expectedRateRange,
        String robustness,
        String conclusion
) {
}
