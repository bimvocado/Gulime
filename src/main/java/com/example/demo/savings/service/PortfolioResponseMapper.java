package com.example.demo.savings.service;

import com.example.demo.savings.api.AllocationResponse;
import com.example.demo.savings.api.AvailableProductResponse;
import com.example.demo.savings.api.OptionsResponse;
import com.example.demo.savings.api.PortfolioResponse;
import com.example.demo.savings.api.ResourceBudgetResponse;
import com.example.demo.savings.api.ResourceUsageResponse;
import com.example.demo.savings.domain.AllocationType;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.UserProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PortfolioResponseMapper {

    private final PortfolioCandidateBuilder candidateBuilder = new PortfolioCandidateBuilder();

    public OptionsResponse buildOptionsResponse(
            List<SavingsProduct> products,
            UserProfile profile,
            List<PortfolioCandidate> candidates,
            List<ProductEvaluation> evaluations,
            long allocatable
    ) {
        double minRisk = candidates.stream().mapToDouble(PortfolioCandidate::returnRisk).min().orElse(0.0);
        double maxRisk = candidates.stream().mapToDouble(PortfolioCandidate::returnRisk).max().orElse(0.0);

        List<PortfolioResponse> options = new ArrayList<>();
        String[] optionTypes = {"STABLE", "BALANCED", "AGGRESSIVE"};
        for (int index = 0; index < candidates.size(); index++) {
            options.add(toResponse(optionTypes[index], candidates.get(index), profile, minRisk, maxRisk));
        }

        List<AvailableProductResponse> availability = evaluations.stream()
                .map(evaluation -> {
                    Availability result = productAvailability(evaluation, profile, allocatable);
                    return new AvailableProductResponse(
                            evaluation.product().productId(),
                            evaluation.product().productName(),
                            evaluation.product().termMonths(),
                            toPercent(evaluation.expectedRate()),
                            evaluation.resourceDemand().cardBudget(),
                            result.status(),
                            result.message()
                    );
                })
                .toList();

        Map<String, Integer> firstTradeBudget = new LinkedHashMap<>();
        products.stream()
                .map(SavingsProduct::bankName)
                .distinct()
                .forEach(bank -> firstTradeBudget.put(
                        bank,
                        (profile.existingBanks() != null && profile.existingBanks().contains(bank)) ? 0 : 1
                ));

        return new OptionsResponse(
                new ResourceBudgetResponse(
                        profile.cardBudgetCap(),
                        profile.salaryTransferable() ? 1 : 0,
                        firstTradeBudget,
                        allocatable
                ),
                options,
                availability
        );
    }

    private PortfolioResponse toResponse(
            String optionType,
            PortfolioCandidate candidate,
            UserProfile profile,
            double minRisk,
            double maxRisk
    ) {
        double riskScore = normalize(candidate.returnRisk(), minRisk, maxRisk);
        long expectedReturn = Math.round(candidate.expectedReturn());
        double weightedRate = candidate.principal() == 0L ? 0.0 : candidate.expectedReturn() / candidate.principal() * 100.0;
        long allocatable = Math.max(0L, profile.lumpSum() - profile.emergencyFund());

        int completionMonth = candidate.allocations().stream()
                .mapToInt(allocation -> allocation.slot().startMonth() + allocation.product().product().termMonths())
                .max()
                .orElse(0);

        List<AllocationResponse> allocations = candidate.allocations().stream()
                .map(allocation -> new AllocationResponse(
                        allocation.slotIndex(),
                        allocation.slot().allocationType().name(),
                        allocation.product().product().productId(),
                        allocation.product().product().productName(),
                        allocation.product().product().bankName(),
                        allocation.slot().amount(),
                        allocation.slot().allocationType() == AllocationType.MONTHLY_SAVING
                                ? candidateBuilder.monthlyAmount(allocation.slot()) : 0L,
                        allocation.product().product().termMonths(),
                        allocation.slot().startMonth(),
                        allocation.slot().startMonth() + allocation.product().product().termMonths(),
                        toPercent(allocation.product().expectedRate()),
                        allocation.product().resourceDemand().cardBudget(),
                        "SELECTED",
                        null
                ))
                .toList();

        return new PortfolioResponse(
                optionType,
                candidate.principal() + expectedReturn,
                expectedReturn,
                completionMonth,
                round(weightedRate, 6),
                riskLevel(riskScore),
                round(riskScore, 6),
                true,
                new ResourceUsageResponse(
                        candidate.cardBudgetUsed(),
                        candidate.cardBudgetUsed() > profile.cardBudgetCap(),
                        candidate.salaryTransferUsed(),
                        candidate.salaryTransferUsed() > (profile.salaryTransferable() ? 1 : 0),
                        candidate.cashBalanceUsed(),
                        candidate.cashBalanceUsed() > allocatable
                ),
                allocations
        );
    }

    private Availability productAvailability(ProductEvaluation evaluation, UserProfile profile, long allocatable) {
        if (!evaluation.hardRequirementsSatisfied()) {
            return new Availability("DISABLED_HARD_REQUIREMENT", "필수 우대조건을 만족할 수 없습니다.");
        }
        if (evaluation.resourceDemand().cardBudget() > profile.cardBudgetCap()) {
            return new Availability("DISABLED_EXCEEDED_RESOURCE", "월 카드 사용 예산을 초과합니다.");
        }
        if (evaluation.resourceDemand().salaryTransfer() > (profile.salaryTransferable() ? 1 : 0)) {
            return new Availability("DISABLED_SALARY_TRANSFER", "급여이체가 불가능합니다.");
        }
        if (evaluation.resourceDemand().cashBalance() > allocatable) {
            return new Availability("DISABLED_INSUFFICIENT_CASH", "비상금을 제외한 가용 현금이 부족합니다.");
        }
        if (!evaluation.resourceDemand().firstTradeByBank().isEmpty() && profile.existingBanks().contains(evaluation.product().bankName())) {
            return new Availability("DISABLED_FIRST_TRADE", "기존 거래은행이라 첫 거래 우대를 받을 수 없습니다.");
        }
        return new Availability("AVAILABLE", null);
    }

    private double normalize(double value, double minimum, double maximum) {
        return maximum == minimum ? 0.0 : (value - minimum) / (maximum - minimum);
    }

    private String riskLevel(double riskScore) {
        if (riskScore < 0.33) return "LOW";
        if (riskScore < 0.66) return "MEDIUM";
        return "HIGH";
    }

    private static double toPercent(double decimalRate) {
        return round(decimalRate * 100.0, 6);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private record Availability(String status, String message) {}
}