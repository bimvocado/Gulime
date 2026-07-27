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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                    case CARD_BUDGET -> cardBudget += safeThreshold(condition);
                    case SALARY_TRANSFER -> salaryTransfer++;
                    case FIRST_TRADE -> firstTradeByBank.merge(
                            product.bankName(),
                            1,
                            Integer::sum
                    );
                    case CASH_BALANCE -> cashBalance += safeThreshold(condition);
                    case NONE -> {
                    }
                }
            }
        }

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
            case CARD_PAYMENT_ACCOUNT, CARD_OWNERSHIP, PRODUCT_HOLDING,
                    TRANSFER_COUNT, CHANNEL_USE, MARKETING_CONSENT, OTHER ->
                    binary(condition, true, null);
        };
    }

    private ConditionEvaluation cardSpend(
            ProductCondition condition,
            UserProfile profile
    ) {
        boolean achievable = profile.cardBudgetCap() >= condition.threshold();
        if (!achievable) {
            return binary(
                    condition,
                    false,
                    "카드 예산 상한이 조건 금액보다 낮습니다."
            );
        }

        long conditionSeed = MONTE_CARLO_SEED + condition.conditionId().hashCode();
        MonteCarloResult result = monteCarlo.simulateCardCondition(
                profile.cardSpend6m(),
                profile.cardBudgetCap(),
                condition.threshold(),
                condition.periodMonths(),
                condition.requiredMonths(),
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
                true
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
                true
        );
    }

    private List<ConditionEvaluation> selectConditions(
            List<ConditionEvaluation> candidates
    ) {
        List<ConditionEvaluation> result = new ArrayList<>();
        Map<String, List<ConditionEvaluation>> grouped = new LinkedHashMap<>();

        for (ConditionEvaluation candidate : candidates) {
            String group = candidate.condition().selectionGroup();
            if (group == null || group.isBlank()) {
                result.add(candidate.withSelected(true));
            } else {
                grouped.computeIfAbsent(group, ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }

        for (List<ConditionEvaluation> group : grouped.values()) {
            List<ConditionEvaluation> branchSelected = chooseBestBranch(group);
            int maxSelect = branchSelected.stream()
                    .map(ConditionEvaluation::condition)
                    .map(ProductCondition::maxSelect)
                    .filter(value -> value != null && value > 0)
                    .findFirst()
                    .orElse(branchSelected.size());

            List<ConditionEvaluation> chosen = branchSelected.stream()
                    .sorted(Comparator.comparingDouble(this::expectedContribution)
                            .reversed())
                    .limit(maxSelect)
                    .toList();

            for (ConditionEvaluation candidate : group) {
                result.add(candidate.withSelected(chosen.contains(candidate)));
            }
        }

        return result;
    }

    private List<ConditionEvaluation> chooseBestBranch(
            List<ConditionEvaluation> group
    ) {
        Map<String, List<ConditionEvaluation>> branches = group.stream()
                .collect(Collectors.groupingBy(
                        item -> item.condition().branch() == null
                                ? "__DEFAULT__"
                                : item.condition().branch()
                ));
        if (branches.size() <= 1) {
            return group;
        }
        return branches.values().stream()
                .max(Comparator.comparingDouble(branch -> branch.stream()
                        .mapToDouble(this::expectedContribution)
                        .sum()))
                .orElse(List.of());
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
}

