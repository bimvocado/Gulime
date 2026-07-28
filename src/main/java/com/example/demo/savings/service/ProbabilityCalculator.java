package com.example.demo.savings.service;

import java.util.List;

public final class ProbabilityCalculator {

    private ProbabilityCalculator() {
    }

    public static double mean(List<Long> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        return values.stream().mapToLong(Long::longValue).average().orElseThrow();
    }

    public static double sampleStandardDeviation(List<Long> values) {
        if (values == null || values.size() < 2) {
            throw new IllegalArgumentException("at least two observations are required");
        }
        double mean = mean(values);
        double squaredDifferences = values.stream()
                .mapToDouble(value -> {
                    double difference = value - mean;
                    return difference * difference;
                })
                .sum();
        return Math.sqrt(squaredDifferences / (values.size() - 1));
    }
}

