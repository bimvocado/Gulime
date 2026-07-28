package com.example.demo.savings.service;

public record MonteCarloResult(
        double probability,
        double lowerProbability,
        double upperProbability,
        double variancePlusProbability,
        double varianceMinusProbability
) {
}

