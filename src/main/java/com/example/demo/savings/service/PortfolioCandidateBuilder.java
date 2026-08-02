package com.example.demo.savings.service;

import com.example.demo.savings.domain.AllocationSlot;
import com.example.demo.savings.domain.AllocationType;
import com.example.demo.savings.domain.ProductType;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.UserProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PortfolioCandidateBuilder {

    public PortfolioCandidate buildGreedyCandidate(
            String optionType,
            List<AllocationSlot> slots,
            List<ProductEvaluation> evaluations,
            UserProfile profile,
            double riskTolerance
    ) {
        List<PortfolioAllocation> selected = new ArrayList<>();
        int targetMonths = profile.targetMonths() > 0 ? profile.targetMonths() : 12;

        allocateUnifiedBudget(
                optionType,
                Math.max(0L, profile.lumpSum() - profile.emergencyFund()),
                profile.monthlySaving(),
                targetMonths,
                evaluations,
                profile,
                selected
        );

        return buildCandidate(selected);
    }

    private void allocateUnifiedBudget(
            String optionType,
            long initialCash,
            long monthlyCashFlow,
            int targetMonths,
            List<ProductEvaluation> evaluations,
            UserProfile profile,
            List<PortfolioAllocation> selected
    ) {
        long totalBudget = safeAdd(
                initialCash,
                safeMultiply(monthlyCashFlow, targetMonths)
        );
        if (totalBudget <= 0L) {
            return;
        }

        // The evaluator currently models a constant expected rate within each
        // product's limit. Therefore the best marginal use of the next won is
        // to fill the highest return-per-won product first, then move on.
        Comparator<ProductEvaluation> ranking = Comparator
                .comparingDouble((ProductEvaluation evaluation) ->
                        marginalReturnScore(
                                evaluation,
                                allocationType(evaluation),
                                optionType,
                                profile
                        ))
                .reversed()
                .thenComparing(evaluation -> evaluation.product().productId());

        List<ProductEvaluation> ranked = evaluations.stream()
                .filter(eval -> eval.product() != null)
                .filter(eval -> eval.product().productType() == ProductType.DEPOSIT
                        || eval.product().productType() == ProductType.SAVING)
                .sorted(ranking)
                .toList();

        Map<AllocationType, Integer> typeIndexes = new HashMap<>();
        for (ProductEvaluation evaluation : ranked) {
            SavingsProduct product = evaluation.product();
            AllocationType allocationType = allocationType(evaluation);
            PortfolioCandidate current = buildCandidate(selected);
            long remainingPrincipal = totalBudget - current.principal();
            if (remainingPrincipal <= 0L) {
                break;
            }

            long maximum = product.maximumAmount() <= 0L
                    ? Long.MAX_VALUE
                    : product.maximumAmount();
            long principalAmount;
            if (allocationType == AllocationType.LUMP_SUM) {
                maximum = Math.min(maximum, 50_000_000L);
                long remainingInitialCash = initialCash - current.lumpSumPrincipal();
                principalAmount = Math.min(
                        remainingPrincipal,
                        Math.min(remainingInitialCash, maximum)
                );
                if (principalAmount < product.minimumAmount()) {
                    continue;
                }
            } else {
                long maximumMonthly = maximum;
                long remainingMonthlyCapacity = remainingPrincipal / targetMonths;
                long monthlyAmount = Math.min(remainingMonthlyCapacity, maximumMonthly);
                if (monthlyAmount < product.minimumAmount()) {
                    continue;
                }
                principalAmount = safeMultiply(monthlyAmount, targetMonths);
            }

            if (principalAmount <= 0L) {
                continue;
            }

            int typeIndex = typeIndexes.getOrDefault(allocationType, 0);
            int startMonth = calculateStartMonth(optionType, allocationType, typeIndex);
            AllocationSlot slot = new AllocationSlot(
                    allocationType,
                    principalAmount,
                    targetMonths,
                    startMonth
            );
            PortfolioAllocation allocation = new PortfolioAllocation(
                    selected.size(),
                    slot,
                    evaluation
            );

            List<PortfolioAllocation> tentative = new ArrayList<>(selected);
            tentative.add(allocation);
            if (!isFeasible(buildCandidate(tentative), profile)) {
                continue;
            }

            selected.add(allocation);
            typeIndexes.put(allocationType, typeIndex + 1);
        }
    }

    private AllocationType allocationType(ProductEvaluation evaluation) {
        return evaluation.product().productType() == ProductType.SAVING
                ? AllocationType.MONTHLY_SAVING
                : AllocationType.LUMP_SUM;
    }

    private double marginalReturnScore(
            ProductEvaluation evaluation,
            AllocationType allocationType,
            String optionType,
            UserProfile profile
    ) {
        double durationFactor = allocationType == AllocationType.MONTHLY_SAVING
                ? (evaluation.product().termMonths() + 1.0) / 24.0
                : evaluation.product().termMonths() / 12.0;
        return greedyScore(evaluation, optionType, profile) * durationFactor;
    }

    private long safeMultiply(long value, int multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private long safeAdd(long left, long right) {
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private int calculateStartMonth(String optionType, AllocationType type, int slotIndex) {
        if (type == AllocationType.LUMP_SUM) return 0;

        return switch (optionType) {
            case "STABLE" -> slotIndex;
            case "BALANCED" -> slotIndex / 2;
            case "AGGRESSIVE" -> 0;
            default -> slotIndex;
        };
    }

    private double greedyScore(ProductEvaluation evaluation, String optionType, UserProfile profile) {
        double baseRate = evaluation.expectedRate();
        long cardDemand = evaluation.resourceDemand() != null ? evaluation.resourceDemand().cardBudget() : 0L;

        switch (optionType) {
            case "STABLE":
                return baseRate - (cardDemand * 0.0000001) - (0.5 * evaluation.rateRisk());
            case "BALANCED":
                double balancedBonus = (cardDemand > 0 && cardDemand <= profile.cardBudgetCap()) ? 0.002 : 0.0;
                return baseRate + balancedBonus - (0.2 * evaluation.rateRisk());
            case "AGGRESSIVE":
                double aggressiveBonus = (cardDemand > 0) ? 0.005 : 0.001;
                return baseRate + aggressiveBonus - (0.01 * evaluation.rateRisk());
            default:
                return baseRate;
        }
    }

    public PortfolioCandidate buildCandidate(List<PortfolioAllocation> allocations) {
        long principal = 0L;
        long lumpSumPrincipal = 0L;
        double expectedReturn = 0.0;
        double variance = 0.0;
        long totalCardBudget = 0L;
        int salary = 0;
        long cash = 0L;
        Map<String, Integer> firstTrade = new HashMap<>();

        for (PortfolioAllocation allocation : allocations) {
            AllocationSlot slot = allocation.slot();
            ProductEvaluation product = allocation.product();
            principal += slot.amount();
            if (slot.allocationType() == AllocationType.LUMP_SUM) {
                lumpSumPrincipal += slot.amount();
            }

            double durationFactor = slot.allocationType() == AllocationType.MONTHLY_SAVING
                    ? (product.product().termMonths() + 1.0) / 24.0
                    : product.product().termMonths() / 12.0;

            expectedReturn += slot.amount() * product.expectedRate() * durationFactor;
            double riskWon = slot.amount() * product.rateRisk() * durationFactor;
            variance += riskWon * riskWon;

            if (product.resourceDemand() != null) {
                totalCardBudget += product.resourceDemand().cardBudget();
                salary = Math.max(salary, product.resourceDemand().salaryTransfer());
                cash = Math.max(cash, product.resourceDemand().cashBalance());
                if (product.resourceDemand().firstTradeByBank() != null) {
                    product.resourceDemand().firstTradeByBank()
                            .forEach((bank, count) -> firstTrade.merge(bank, count, Math::max));
                }
            }
        }

        return new PortfolioCandidate(
                allocations,
                principal,
                lumpSumPrincipal,
                expectedReturn,
                Math.sqrt(variance),
                totalCardBudget,
                salary,
                cash,
                firstTrade
        );
    }

    private boolean isFeasible(PortfolioCandidate candidate, UserProfile profile) {
        long allocatable = Math.max(0L, profile.lumpSum() - profile.emergencyFund());
        int targetMonths = profile.targetMonths() > 0 ? profile.targetMonths() : 12;

        long totalMonthlySavingDemand = candidate.allocations().stream()
                .filter(allocation -> allocation.slot().allocationType() == AllocationType.MONTHLY_SAVING)
                .mapToLong(allocation -> monthlyAmount(allocation.slot()))
                .sum();
        long initialCashReservedForSavings = allocatable - candidate.lumpSumPrincipal();
        long monthlySavingCapacity = safeAdd(
                profile.monthlySaving(),
                initialCashReservedForSavings / targetMonths
        );
        long totalBudget = safeAdd(
                allocatable,
                safeMultiply(profile.monthlySaving(), targetMonths)
        );

        if (candidate.lumpSumPrincipal() > allocatable
                || candidate.principal() > totalBudget
                || totalMonthlySavingDemand > monthlySavingCapacity + 1000L
                || candidate.cardBudgetUsed() > profile.cardBudgetCap()
                || candidate.salaryTransferUsed() > (profile.salaryTransferable() ? 1 : 0)
                || candidate.cashBalanceUsed() > allocatable
                || candidate.allocations().stream().anyMatch(a -> !a.product().hardRequirementsSatisfied())) {
            return false;
        }

        return candidate.firstTradeUsed().values().stream().allMatch(count -> count <= 1);
    }

    public long monthlyAmount(AllocationSlot slot) {
        return slot.termMonths() == 0 ? 0L : slot.amount() / slot.termMonths();
    }
}
