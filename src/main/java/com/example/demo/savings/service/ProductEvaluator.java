package com.example.demo.savings.service;

import com.example.demo.savings.domain.ConditionType;
import com.example.demo.savings.domain.ParseStatus;
import com.example.demo.savings.domain.ProductCondition;
import com.example.demo.savings.domain.ResourceType;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.UserProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class ProductEvaluator {

    private static final long MONTE_CARLO_SEED = 20_260_726L;
    private final MonteCarloSimulator monteCarlo = new MonteCarloSimulator();

    public ProductEvaluation evaluate(SavingsProduct product, UserProfile profile) {
        List<ProductCondition> parseExcluded = new ArrayList<>();
        List<ConditionEvaluation> candidates = new ArrayList<>();

        for (ProductCondition condition : product.conditions()) {
            if (condition.parseStatus() != ParseStatus.COMPLETE
                    || !hasRequiredData(condition)) {
                parseExcluded.add(condition);
                continue;
            }
            candidates.add(evaluateCondition(product, condition, profile));
        }

        List<ConditionEvaluation> selected = selectConditions(candidates);
        double expectedRate = product.baseRate();
        double lowerRate = product.baseRate();
        double upperRate = product.baseRate();
        double maxRate = product.baseRate();
        double plusRate = product.baseRate();
        double minusRate = product.baseRate();
        double variance = 0.0;
        boolean hardSatisfied = true;

        long cardBudget = 0L;
        int salaryTransfer = 0;
        long cashBalance = 0L;
        Map<String, Integer> firstTradeByBank = new HashMap<>();

        for (ConditionEvaluation evaluation : selected) {
            if (!evaluation.selected()) {
                continue;
            }
            ProductCondition condition = evaluation.condition();
            double bonus = condition.rateBonus();
            expectedRate += evaluation.probability() * bonus;
            lowerRate += evaluation.lowerProbability() * bonus;
            upperRate += evaluation.upperProbability() * bonus;
            plusRate += evaluation.variancePlusProbability() * bonus;
            minusRate += evaluation.varianceMinusProbability() * bonus;
            variance += evaluation.probability()
                    * (1.0 - evaluation.probability())
                    * bonus * bonus;

            if (evaluation.achievable()) {
                maxRate += bonus;
            }
            if (condition.hardRequirement() && !evaluation.achievable()) {
                hardSatisfied = false;
            }

            if (evaluation.achievable()) {
                switch (condition.resource()) {
                    case CARD_BUDGET -> cardBudget = Math.max(
                            cardBudget,
                            monthlyThreshold(condition)
                    );
                    case SALARY_TRANSFER -> salaryTransfer = 1;
                    case FIRST_TRADE -> firstTradeByBank.merge(
                            product.bankName(),
                            1,
                            Math::max
                    );
                    case CASH_BALANCE -> cashBalance = Math.max(
                            cashBalance,
                            safeThreshold(condition)
                    );
                    case NONE -> {
                    }
                }
            }
        }

        // 💡 [버그 수정] 기대금리 및 관련 금리 지표들이 상품의 '최고금리(product.maxRate())'를 초과하지 않도록 보정
        double productMaxLimit = product.maxRate() > 0 ? product.maxRate() : maxRate;

        expectedRate = Math.min(expectedRate, productMaxLimit);
        lowerRate = Math.min(lowerRate, productMaxLimit);
        upperRate = Math.min(upperRate, productMaxLimit);
        plusRate = Math.min(plusRate, productMaxLimit);
        minusRate = Math.min(minusRate, productMaxLimit);
        maxRate = Math.min(maxRate, productMaxLimit);

        return new ProductEvaluation(
                product,
                expectedRate,
                lowerRate,
                upperRate,
                maxRate,
                plusRate,
                minusRate,
                Math.sqrt(variance),
                hardSatisfied,
                selected,
                parseExcluded,
                new ResourceDemand(
                        cardBudget,
                        salaryTransfer,
                        firstTradeByBank,
                        cashBalance
                )
        );
    }

    private ConditionEvaluation evaluateCondition(
            SavingsProduct product,
            ProductCondition condition,
            UserProfile profile
    ) {
        return switch (condition.type()) {
            case CARD_SPEND -> cardSpend(condition, profile);
            case SALARY_TRANSFER -> binary(
                    condition,
                    profile.salaryTransferable(),
                    "급여이체가 불가능한 프로필입니다."
            );
            case FIRST_TRADE -> binary(
                    condition,
                    !profile.existingBanks().contains(product.bankName()),
                    "기존 거래 은행이므로 첫 거래 우대를 받을 수 없습니다."
            );
            case BALANCE_MAINTENANCE, DEPOSIT_AMOUNT -> binary(
                    condition,
                    profile.lumpSum() - profile.emergencyFund() >= safeThreshold(condition),
                    "배분 가능 현금이 조건 금액보다 적습니다."
            );
            case UNCONDITIONAL -> binary(condition, true, null);
            case CARD_PAYMENT_ACCOUNT, CARD_OWNERSHIP, PRODUCT_HOLDING,
                 TRANSFER_COUNT, CHANNEL_USE, MARKETING_CONSENT, OTHER ->
                    confirmation(condition, profile);
        };
    }

    private ConditionEvaluation confirmation(
            ProductCondition condition,
            UserProfile profile
    ) {
        Boolean answer = profile.conditionAnswers().get(condition.conditionId());
        if (answer != null) {
            return binary(
                    condition,
                    answer,
                    answer ? null : "사용자가 달성할 수 없다고 확인한 조건입니다."
            );
        }

        return new ConditionEvaluation(
                condition,
                true,
                0.5,
                0.0,
                1.0,
                1.0,
                0.0,
                "정확한 금리 계산을 위해 사용자 확인이 필요한 조건입니다.",
                true,
                true
        );
    }

    private ConditionEvaluation cardSpend(
            ProductCondition condition,
            UserProfile profile
    ) {
        long requiredMonthly = monthlyThreshold(condition);

        double cardMean = ProbabilityCalculator.mean(profile.cardSpend6m());
        long effectiveCap = Math.max(profile.cardBudgetCap(), (long) cardMean);

        boolean achievable = effectiveCap >= requiredMonthly;

        if (!achievable) {
            return binary(
                    condition,
                    false,
                    "월 카드 예산 상한 및 최근 사용액이 필요 월 카드 실적보다 낮습니다."
            );
        }

        long conditionSeed = MONTE_CARLO_SEED + condition.conditionId().hashCode();

        MonteCarloResult result = monteCarlo.simulateCardCondition(
                profile.cardSpend6m(),
                profile.cardBudgetCap(),
                requiredMonthly,
                condition.periodMonths() != null ? condition.periodMonths() : 1,
                condition.requiredMonths() != null ? condition.requiredMonths() : 1,
                conditionSeed
        );
        return new ConditionEvaluation(
                condition,
                true,
                result.probability(),
                result.lowerProbability(),
                result.upperProbability(),
                result.variancePlusProbability(),
                result.varianceMinusProbability(),
                null,
                true,
                false
        );
    }

    private ConditionEvaluation binary(
            ProductCondition condition,
            boolean achievable,
            String failureReason
    ) {
        double probability = achievable ? 1.0 : 0.0;
        return new ConditionEvaluation(
                condition,
                achievable,
                probability,
                probability,
                probability,
                probability,
                probability,
                achievable ? null : failureReason,
                true,
                false
        );
    }

    private List<ConditionEvaluation> selectConditions(
            List<ConditionEvaluation> candidates
    ) {
        Set<ConditionEvaluation> active = new LinkedHashSet<>(candidates);

        retainBestPerGroup(
                active,
                item -> item.condition().tierGroup(),
                ignored -> 1
        );
        retainBestBranch(active);

        retainBestPerGroup(
                active,
                item -> item.condition().exclusiveGroup(),
                ignored -> 1
        );

        retainBestPerGroup(
                active,
                item -> item.condition().selectionGroup(),
                group -> group.stream()
                        .map(ConditionEvaluation::condition)
                        .map(ProductCondition::maxSelect)
                        .filter(value -> value != null && value > 0)
                        .findFirst()
                        .orElse(group.size())
        );

        return candidates.stream()
                .map(candidate -> candidate.withSelected(active.contains(candidate)))
                .toList();
    }

    private void retainBestPerGroup(
            Set<ConditionEvaluation> active,
            Function<ConditionEvaluation, String> groupKey,
            Function<List<ConditionEvaluation>, Integer> limitProvider
    ) {
        Map<String, List<ConditionEvaluation>> groups = new LinkedHashMap<>();
        for (ConditionEvaluation candidate : active) {
            String key = groupKey.apply(candidate);
            if (key != null && !key.isBlank()) {
                groups.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }

        for (List<ConditionEvaluation> group : groups.values()) {
            int limit = Math.max(0, limitProvider.apply(group));
            Set<ConditionEvaluation> retained = group.stream()
                    .sorted(Comparator
                            .comparingDouble(this::expectedContribution)
                            .reversed()
                            .thenComparing(item ->
                                    item.condition().conditionId()))
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toCollection(
                            LinkedHashSet::new
                    ));
            active.removeIf(candidate ->
                    group.contains(candidate) && !retained.contains(candidate));
        }
    }

    private void retainBestBranch(Set<ConditionEvaluation> active) {
        Map<String, List<ConditionEvaluation>> branches = new LinkedHashMap<>();
        for (ConditionEvaluation candidate : active) {
            String branch = candidate.condition().branch();
            if (branch != null && !branch.isBlank()) {
                branches.computeIfAbsent(branch, ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }
        if (branches.size() <= 1) {
            return;
        }

        String selectedBranch = branches.entrySet().stream()
                .max(Comparator
                        .<Map.Entry<String, List<ConditionEvaluation>>>
                                comparingDouble(entry -> entry.getValue().stream()
                                .mapToDouble(this::expectedContribution)
                                .sum())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElseThrow();

        active.removeIf(candidate -> {
            String branch = candidate.condition().branch();
            return branch != null
                    && !branch.isBlank()
                    && !branch.equals(selectedBranch);
        });
    }

    private double expectedContribution(ConditionEvaluation evaluation) {
        return evaluation.probability() * evaluation.condition().rateBonus();
    }

    private boolean hasRequiredData(ProductCondition condition) {
        if (condition.rateBonus() == null) {
            return false;
        }
        if (condition.type() != ConditionType.CARD_SPEND) {
            return true;
        }
        return condition.threshold() != null
                && condition.periodMonths() != null
                && condition.requiredMonths() != null;
    }

    private long safeThreshold(ProductCondition condition) {
        return condition.threshold() == null ? 0L : condition.threshold();
    }

    private long monthlyThreshold(ProductCondition condition) {
        long threshold = safeThreshold(condition);
        int period = (condition.periodMonths() != null && condition.periodMonths() > 0)
                ? condition.periodMonths()
                : 1;
        return threshold / period;
    }
}