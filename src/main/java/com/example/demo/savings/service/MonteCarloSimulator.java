package com.example.demo.savings.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

public final class MonteCarloSimulator {

    public static final int MAIN_ITERATIONS = 10_000;
    public static final int BOOTSTRAP_ITERATIONS = 1_000;
    private static final int BOOTSTRAP_INNER_ITERATIONS = 1_000;

    public MonteCarloResult simulateCardCondition(
            List<Long> cardSpend6m,
            long cardBudgetCap,
            long threshold,
            int periodMonths,
            int requiredMonths,
            long seed
    ) {
        if (cardBudgetCap < threshold) {
            return new MonteCarloResult(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        double mean = ProbabilityCalculator.mean(cardSpend6m);
        double std = ProbabilityCalculator.sampleStandardDeviation(cardSpend6m);
        double probability = simulateAchievement(
                mean,
                std,
                cardBudgetCap,
                threshold,
                periodMonths,
                requiredMonths,
                MAIN_ITERATIONS,
                seed
        );
        double[] interval = bootstrapInterval(
                cardSpend6m,
                cardBudgetCap,
                threshold,
                periodMonths,
                requiredMonths,
                seed + 1
        );
        double plus = simulateAchievement(
                mean,
                std * 1.3,
                cardBudgetCap,
                threshold,
                periodMonths,
                requiredMonths,
                MAIN_ITERATIONS,
                seed + 2
        );
        double minus = simulateAchievement(
                mean,
                std * 0.7,
                cardBudgetCap,
                threshold,
                periodMonths,
                requiredMonths,
                MAIN_ITERATIONS,
                seed + 3
        );

        return new MonteCarloResult(
                probability,
                interval[0],
                interval[1],
                plus,
                minus
        );
    }

    double simulateAchievement(
            double mean,
            double std,
            long cardBudgetCap,
            long threshold,
            int periodMonths,
            int requiredMonths,
            int iterations,
            long seed
    ) {
        SplittableRandom random = new SplittableRandom(seed);
        int successfulPaths = 0;

        for (int simulation = 0; simulation < iterations; simulation++) {
            int achievedMonths = 0;
            for (int month = 0; month < periodMonths; month++) {
                double generated = Math.max(0.0, mean + std * nextGaussian(random));
                double capped = Math.min(generated, cardBudgetCap);
                if (capped >= threshold) {
                    achievedMonths++;
                }
            }
            if (achievedMonths >= requiredMonths) {
                successfulPaths++;
            }
        }

        return (double) successfulPaths / iterations;
    }

    private double[] bootstrapInterval(
            List<Long> history,
            long cardBudgetCap,
            long threshold,
            int periodMonths,
            int requiredMonths,
            long seed
    ) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Double> probabilities = new ArrayList<>(BOOTSTRAP_ITERATIONS);

        for (int bootstrap = 0; bootstrap < BOOTSTRAP_ITERATIONS; bootstrap++) {
            List<Long> resample = new ArrayList<>(history.size());
            for (int index = 0; index < history.size(); index++) {
                resample.add(history.get(random.nextInt(history.size())));
            }

            double mean = ProbabilityCalculator.mean(resample);
            double std = ProbabilityCalculator.sampleStandardDeviation(resample);
            probabilities.add(simulateAchievement(
                    mean,
                    std,
                    cardBudgetCap,
                    threshold,
                    periodMonths,
                    requiredMonths,
                    BOOTSTRAP_INNER_ITERATIONS,
                    random.nextLong()
            ));
        }

        Collections.sort(probabilities);
        return new double[]{
                percentile(probabilities, 0.05),
                percentile(probabilities, 0.95)
        };
    }

    private double nextGaussian(SplittableRandom random) {
        double u1 = Math.max(random.nextDouble(), 1.0e-12);
        double u2 = random.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    private double percentile(List<Double> sorted, double quantile) {
        double index = quantile * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double weight = index - lower;
        return sorted.get(lower) * (1.0 - weight) + sorted.get(upper) * weight;
    }
}

