package com.example.demo.savings.service;

import com.example.demo.savings.api.AllocationResponse;
import com.example.demo.savings.api.AvailableProductResponse;
import com.example.demo.savings.api.OptionsResponse;
import com.example.demo.savings.api.PortfolioResponse;
import com.example.demo.savings.api.ResourceBudgetResponse;
import com.example.demo.savings.api.ResourceUsageResponse;
import com.example.demo.savings.domain.AllocationSlot;
import com.example.demo.savings.domain.AllocationType;
import com.example.demo.savings.domain.ProductType;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.UserProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 슬롯마다 현재 자원 예산 안에서 가장 점수가 높은 상품을 선택하는 Greedy 최적화기입니다.
 *
 * <p>모든 조합을 열거하지 않기 때문에 상품 수가 늘어나도
 * 슬롯 수 × 상품 수 수준으로 계산량이 증가합니다.</p>
 */
public final class PortfolioOptimizer {

    private final ProductEvaluator evaluator = new ProductEvaluator();

    public OptionsResponse optimize(
            List<SavingsProduct> products,
            UserProfile profile,
            double riskTolerance,
            List<AllocationSlot> slots
    ) {
        List<ProductEvaluation> evaluations = products.stream()
                .map(product -> evaluator.evaluate(product, profile))
                .toList();

        List<PortfolioCandidate> candidates = List.of(
                buildGreedyCandidate(
                        "STABLE",
                        slots,
                        evaluations,
                        profile,
                        riskTolerance
                ),
                buildGreedyCandidate(
                        "BALANCED",
                        slots,
                        evaluations,
                        profile,
                        riskTolerance
                ),
                buildGreedyCandidate(
                        "AGGRESSIVE",
                        slots,
                        evaluations,
                        profile,
                        riskTolerance
                )
        );

        double minRisk = candidates.stream()
                .mapToDouble(PortfolioCandidate::returnRisk)
                .min()
                .orElse(0.0);
        double maxRisk = candidates.stream()
                .mapToDouble(PortfolioCandidate::returnRisk)
                .max()
                .orElse(0.0);

        List<PortfolioResponse> options = new ArrayList<>();
        String[] optionTypes = {"STABLE", "BALANCED", "AGGRESSIVE"};
        for (int index = 0; index < candidates.size(); index++) {
            options.add(toResponse(
                    optionTypes[index],
                    candidates.get(index),
                    profile,
                    minRisk,
                    maxRisk
            ));
        }

        long allocatable = Math.max(
                0L,
                profile.lumpSum() - profile.emergencyFund()
        );
        List<AvailableProductResponse> availability = evaluations.stream()
                .map(evaluation -> {
                    Availability result = productAvailability(
                            evaluation,
                            profile,
                            allocatable
                    );
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

    private PortfolioCandidate buildGreedyCandidate(
            String optionType,
            List<AllocationSlot> slots,
            List<ProductEvaluation> evaluations,
            UserProfile profile,
            double riskTolerance
    ) {
        List<PortfolioAllocation> selected = new ArrayList<>();

        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            AllocationSlot slot = slots.get(slotIndex);
            final int currentSlotIndex = slotIndex;

            // 1차 시도: 모든 제약조건(Feasible, 금액한도, 기간) 만족하는 최적 상품
            ProductEvaluation chosen = evaluations.stream()
                    .filter(evaluation -> compatible(slot, evaluation.product()))
                    .filter(evaluation -> amountWithinLimit(slot, evaluation.product()))
                    .sorted(Comparator
                            .comparingDouble((ProductEvaluation evaluation) ->
                                    greedyScore(evaluation, optionType, riskTolerance))
                            .reversed()
                            .thenComparing(evaluation -> evaluation.product().productId()))
                    .filter(evaluation -> {
                        List<PortfolioAllocation> tentative = new ArrayList<>(selected);
                        tentative.add(new PortfolioAllocation(currentSlotIndex, slot, evaluation));
                        return isFeasible(buildCandidate(tentative), profile);
                    })
                    .findFirst()
                    // 🎯 2차 시도 (Fallback): 기간 조건은 빼고 "타입(SAVING 등)"만 맞는 상품 중 1위 무조건 선택
                    .orElseGet(() -> evaluations.stream()
                            // 🎯 compatible 대신 compatibleTypeOnly 사용! (기간 제약 완화)
                            .filter(evaluation -> compatibleTypeOnly(slot, evaluation.product()))
                            .sorted(Comparator.comparingDouble((ProductEvaluation evaluation) ->
                                            greedyScore(evaluation, optionType, riskTolerance))
                                    .reversed())
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    slot.allocationType() + " 슬롯에 타입이 일치하는 DB 상품이 전혀 없습니다."
                            ))
                    );

            selected.add(new PortfolioAllocation(
                    slotIndex,
                    slot,
                    chosen
            ));
        }

        return buildCandidate(selected);
    }

    private boolean compatibleTypeOnly(
            AllocationSlot slot,
            SavingsProduct product
    ) {
        // Fallback용: compatible과 동일하게 타입 매칭 진행
        return compatible(slot, product);
    }
    private boolean compatible(
            AllocationSlot slot,
            SavingsProduct product
    ) {
        if (product == null || product.productType() == null) {
            return false;
        }

        if (slot.allocationType() == AllocationType.MONTHLY_SAVING) {
            return product.productType() == ProductType.SAVING;
        }

        return product.productType() != ProductType.SAVING;
    }

    private boolean amountWithinLimit(
            AllocationSlot slot,
            SavingsProduct product
    ) {
        long comparisonAmount = slot.allocationType() == AllocationType.MONTHLY_SAVING
                ? monthlyAmount(slot)
                : slot.amount();

        // 한도 데이터가 없거나 0이면 제한 없음으로 처리
        long min = product.minimumAmount();
        long max = product.maximumAmount() == 0 ? Long.MAX_VALUE : product.maximumAmount();

        // 적금 상품 한도 방어 코드: 월 납입액(comparisonAmount) 또는 총액(slot.amount()) 둘 중 하나라도 범위 내면 허용
        if (slot.allocationType() == AllocationType.MONTHLY_SAVING) {
            return (comparisonAmount >= min && comparisonAmount <= max)
                    || (slot.amount() >= min && slot.amount() <= max)
                    || min == 0;
        }

        return comparisonAmount >= min && comparisonAmount <= max;
    }

    private double greedyScore(
            ProductEvaluation evaluation,
            String optionType,
            double riskTolerance
    ) {
        double riskPenalty = switch (optionType) {
            case "STABLE" -> 3.0;
            case "BALANCED" -> 1.5 - riskTolerance;
            case "AGGRESSIVE" -> 0.25;
            default -> 1.0;
        };
        return evaluation.expectedRate()
                - riskPenalty * evaluation.rateRisk();
    }

    private PortfolioCandidate buildCandidate(
            List<PortfolioAllocation> allocations
    ) {
        long principal = 0L;
        long lumpSumPrincipal = 0L;
        double expectedReturn = 0.0;
        double variance = 0.0;
        long card = 0L;
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

            double durationFactor = slot.allocationType()
                    == AllocationType.MONTHLY_SAVING
                    ? (slot.termMonths() + 1.0) / 24.0
                    : slot.termMonths() / 12.0;
            expectedReturn += slot.amount()
                    * product.expectedRate()
                    * durationFactor;
            double riskWon = slot.amount()
                    * product.rateRisk()
                    * durationFactor;
            variance += riskWon * riskWon;

            card += product.resourceDemand().cardBudget();
            salary += product.resourceDemand().salaryTransfer();
            cash += product.resourceDemand().cashBalance();
            product.resourceDemand().firstTradeByBank()
                    .forEach((bank, count) -> firstTrade.merge(
                            bank,
                            count,
                            Integer::sum
                    ));
        }

        return new PortfolioCandidate(
                allocations,
                principal,
                lumpSumPrincipal,
                expectedReturn,
                Math.sqrt(variance),
                card,
                salary,
                cash,
                firstTrade
        );
    }

    private boolean isFeasible(
            PortfolioCandidate candidate,
            UserProfile profile
    ) {
        long allocatable = Math.max(
                0L,
                profile.lumpSum() - profile.emergencyFund()
        );
        if (candidate.lumpSumPrincipal() > allocatable
                || candidate.cardBudgetUsed() > profile.cardBudgetCap()
                || candidate.salaryTransferUsed()
                > (profile.salaryTransferable() ? 1 : 0)
                || candidate.cashBalanceUsed() > allocatable
                || candidate.allocations().stream()
                .anyMatch(allocation ->
                        !allocation.product().hardRequirementsSatisfied())) {
            return false;
        }

        // 💡 [수정] 첫 거래 우대는 동일 은행당 최대 1개 상품까지만 중복 적용 가능하도록 체크
        return candidate.firstTradeUsed().values().stream()
                .allMatch(count -> count <= 1);
    }
    private PortfolioResponse toResponse(
            String optionType,
            PortfolioCandidate candidate,
            UserProfile profile,
            double minRisk,
            double maxRisk
    ) {
        double riskScore = normalize(
                candidate.returnRisk(),
                minRisk,
                maxRisk
        );
        long expectedReturn = Math.round(candidate.expectedReturn());
        double weightedRate = candidate.principal() == 0L
                ? 0.0
                : candidate.expectedReturn()
                / candidate.principal()
                * 100.0;
        long allocatable = Math.max(
                0L,
                profile.lumpSum() - profile.emergencyFund()
        );

        List<AllocationResponse> allocations = candidate.allocations().stream()
                .map(allocation -> new AllocationResponse(
                        allocation.slotIndex(),
                        allocation.slot().allocationType().name(),
                        allocation.product().product().productId(),
                        allocation.product().product().productName(),
                        allocation.product().product().bankName(),
                        allocation.slot().amount(),
                        allocation.slot().allocationType()
                                == AllocationType.MONTHLY_SAVING
                                ? monthlyAmount(allocation.slot())
                                : 0L,
                        allocation.product().product().termMonths(),
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
                round(weightedRate, 6),
                riskLevel(riskScore),
                round(riskScore, 6),
                true,
                new ResourceUsageResponse(
                        candidate.cardBudgetUsed(),
                        candidate.cardBudgetUsed() > profile.cardBudgetCap(),
                        candidate.salaryTransferUsed(),
                        candidate.salaryTransferUsed()
                                > (profile.salaryTransferable() ? 1 : 0),
                        candidate.cashBalanceUsed(),
                        candidate.cashBalanceUsed() > allocatable
                ),
                allocations
        );
    }

    private Availability productAvailability(
            ProductEvaluation evaluation,
            UserProfile profile,
            long allocatable
    ) {
        if (!evaluation.hardRequirementsSatisfied()) {
            return new Availability(
                    "DISABLED_HARD_REQUIREMENT",
                    "필수 우대조건을 만족할 수 없습니다."
            );
        }
        if (evaluation.resourceDemand().cardBudget()
                > profile.cardBudgetCap()) {
            return new Availability(
                    "DISABLED_EXCEEDED_RESOURCE",
                    "월 카드 사용 예산을 초과합니다."
            );
        }
        if (evaluation.resourceDemand().salaryTransfer()
                > (profile.salaryTransferable() ? 1 : 0)) {
            return new Availability(
                    "DISABLED_SALARY_TRANSFER",
                    "급여이체가 불가능합니다."
            );
        }
        if (evaluation.resourceDemand().cashBalance() > allocatable) {
            return new Availability(
                    "DISABLED_INSUFFICIENT_CASH",
                    "비상금을 제외한 가용 현금이 부족합니다."
            );
        }
        if (!evaluation.resourceDemand().firstTradeByBank().isEmpty()
                && profile.existingBanks().contains(
                evaluation.product().bankName()
        )) {
            return new Availability(
                    "DISABLED_FIRST_TRADE",
                    "기존 거래은행이라 첫 거래 우대를 받을 수 없습니다."
            );
        }
        return new Availability("AVAILABLE", null);
    }

    private long monthlyAmount(AllocationSlot slot) {
        return slot.termMonths() == 0
                ? 0L
                : slot.amount() / slot.termMonths();
    }

    private double normalize(
            double value,
            double minimum,
            double maximum
    ) {
        return maximum == minimum
                ? 0.0
                : (value - minimum) / (maximum - minimum);
    }

    private String riskLevel(double riskScore) {
        if (riskScore < 0.33) {
            return "LOW";
        }
        if (riskScore < 0.66) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private static double toPercent(double decimalRate) {
        return round(decimalRate * 100.0, 6);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record Availability(
            String status,
            String message
    ) {
    }
}
