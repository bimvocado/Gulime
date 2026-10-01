package com.example.demo.savings.service;

import com.example.demo.savings.api.CardBudgetResponse;
import com.example.demo.savings.api.ConditionDiagnosticResponse;
import com.example.demo.savings.api.ConfirmationQuestionResponse;
import com.example.demo.savings.api.ExcludedConditionResponse;
import com.example.demo.savings.api.ProductSimulationResponse;
import com.example.demo.savings.api.RangeResponse;
import com.example.demo.savings.api.SensitivityResponse;
import com.example.demo.savings.api.SimulateResponse;
import com.example.demo.savings.domain.ConditionType;
import com.example.demo.savings.domain.ProductCondition;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.UserProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public final class SimulationEngine {

    private final ProductEvaluator evaluator = new ProductEvaluator();

    public SimulateResponse simulate(List<SavingsProduct> products, UserProfile profile) {
        double cardMean = ProbabilityCalculator.mean(profile.cardSpend6m());
        double cardStd = ProbabilityCalculator.sampleStandardDeviation(profile.cardSpend6m());

        List<ProductSimulationResponse> productResults = products.stream()
                .map(product -> toResponse(
                        evaluator.evaluate(product, profile),
                        profile,
                        products.size() == 1
                ))
                .toList();

        return new SimulateResponse(
                new CardBudgetResponse(
                        Math.round(cardMean),
                        Math.round(cardStd),
                        "NORMAL"
                ),
                productResults
        );
    }

    private ProductSimulationResponse toResponse(
            ProductEvaluation evaluation,
            UserProfile profile,
            boolean includeConfirmationQuestions
    ) {
        List<ConditionDiagnosticResponse> conditions = evaluation.conditions().stream()
                .filter(ConditionEvaluation::selected)
                .map(condition -> new ConditionDiagnosticResponse(
                        condition.condition().conditionId(),
                        condition.condition().conditionName(),
                        condition.condition().sourceText(),
                        toPercent(condition.condition().rateBonus()),
                        condition.achievable(),
                        round(condition.probability(), 8),
                        new RangeResponse(
                                round(condition.lowerProbability(), 8),
                                round(condition.upperProbability(), 8)
                        ),
                        new RangeResponse(
                                round(Math.min(
                                        condition.variancePlusProbability(),
                                        condition.varianceMinusProbability()
                                ), 8),
                                round(Math.max(
                                        condition.variancePlusProbability(),
                                        condition.varianceMinusProbability()
                                ), 8)
                        ),
                        conditionStatus(condition),
                        conditionReason(condition, profile),
                        condition.condition().resource()
                ))
                .toList();

        List<ExcludedConditionResponse> excluded = evaluation.parseExcludedConditions()
                .stream()
                .map(condition -> new ExcludedConditionResponse(
                        condition.conditionName(),
                        condition.parseStatus().name()
                ))
                .toList();

        List<ExcludedConditionResponse> unselected = evaluation.conditions().stream()
                .filter(condition -> !condition.selected())
                .map(condition -> new ExcludedConditionResponse(
                        condition.condition().conditionName(),
                        "NOT_SELECTED_BY_SELECTION_RULE"
                ))
                .toList();
        excluded = java.util.stream.Stream.concat(excluded.stream(), unselected.stream())
                .toList();

        List<ConfirmationQuestionResponse> confirmationQuestions = evaluation.conditions()
                .stream()
                .filter(ignored -> includeConfirmationQuestions)
                .filter(ConditionEvaluation::confirmationRequired)
                .sorted(Comparator
                        .comparingDouble((ConditionEvaluation condition) ->
                                condition.condition().rateBonus())
                        .reversed()
                        .thenComparing(condition -> condition.condition().conditionId()))
                .limit(3)
                .map(condition -> new ConfirmationQuestionResponse(
                        condition.condition().conditionId(),
                        condition.condition().conditionName() + " 조건을 충족할 수 있나요?",
                        toPercent(condition.condition().rateBonus())
                ))
                .toList();

        double sensitivityMinPercent = toPercent(Math.min(
                evaluation.variancePlusRate(),
                evaluation.varianceMinusRate()
        ));
        double sensitivityMaxPercent = toPercent(Math.max(
                evaluation.variancePlusRate(),
                evaluation.varianceMinusRate()
        ));
        String robustness = sensitivityRobustness(
                evaluation.product().maxRate(),
                sensitivityMaxPercent
        );

        return new ProductSimulationResponse(
                evaluation.product().productId(),
                evaluation.product().productName(),
                evaluation.product().bankName(),
                evaluation.product().termMonths(),
                toPercent(evaluation.product().baseRate()),
                toPercent(evaluation.product().maxRate()),
                toPercent(evaluation.expectedRate()),
                new RangeResponse(
                        toPercent(evaluation.lowerRate()),
                        toPercent(evaluation.upperRate())
                ),
                toPercent(evaluation.profileAchievableMaxRate()),
                conditions,
                excluded,
                confirmationQuestions,
                new SensitivityResponse(
                        30,
                        new RangeResponse(
                                sensitivityMinPercent,
                                sensitivityMaxPercent
                        ),
                        robustness,
                        sensitivityConclusion(
                                evaluation.product().maxRate(),
                                sensitivityMaxPercent,
                                robustness
                        )
                )
        );
    }

    private String conditionStatus(ConditionEvaluation evaluation) {
        if (evaluation.confirmationRequired()) {
            return "NEEDS_CONFIRMATION";
        }
        if (!evaluation.achievable() || evaluation.probability() == 0.0) {
            return "FAILED";
        }
        if (evaluation.condition().type() == ConditionType.CARD_SPEND
                && evaluation.probability() < 1.0) {
            return "POSSIBLE_WITH_PROBABILITY";
        }
        return "SUCCESS";
    }

    private String conditionReason(
            ConditionEvaluation evaluation,
            UserProfile profile
    ) {
        if (evaluation.reason() != null) {
            return evaluation.reason();
        }
        if (evaluation.condition().type() == ConditionType.CARD_SPEND) {
            long mean = Math.round(ProbabilityCalculator.mean(profile.cardSpend6m()));
            long standardDeviation = Math.round(
                    ProbabilityCalculator.sampleStandardDeviation(
                            profile.cardSpend6m()
                    )
            );

            ProductCondition condition = evaluation.condition();
            long totalThreshold = condition.threshold() != null ? condition.threshold() : 0L;
            int period = (condition.periodMonths() != null && condition.periodMonths() > 0) ? condition.periodMonths() : 1;
            long monthlyThreshold = totalThreshold / period;

            // 💡 [개선] 사용자가 알아보기 쉽게 월 필요액 및 총 필요액을 함께 안내
            return "최근 6개월 카드 사용액 평균 "
                    + mean
                    + "원, 표준편차 "
                    + standardDeviation
                    + "원을 기준으로 월 평균 "
                    + monthlyThreshold
                    + "원 (총 "
                    + period
                    + "개월간 "
                    + totalThreshold
                    + "원) 조건의 달성확률을 계산했습니다.";
        }
        return "온보딩 프로필 기준으로 달성 가능한 조건입니다.";
    }

    private String sensitivityRobustness(
            double advertisedMaxRate,
            double sensitivityMaxExpectedPercent
    ) {
        double advertisedPercent = advertisedMaxRate * 100.0;
        return advertisedPercent - sensitivityMaxExpectedPercent >= 0.05
                ? "STABLE"
                : "UNSTABLE";
    }

    private String sensitivityConclusion(
            double advertisedMaxRate,
            double sensitivityMaxExpectedPercent,
            String robustness
    ) {
        double advertisedPercent = advertisedMaxRate * 100.0;
        if ("STABLE".equals(robustness)) {
            return "광고 최고금리는 변동성 변화에도 기대금리보다 높습니다.";
        }
        return "변동성 가정에 따라 광고 최고금리 "
                + round(advertisedPercent, 6)
                + "%와의 차이가 달라질 수 있습니다.";
    }

    private static double toPercent(double decimalRate) {
        return round(decimalRate * 100.0, 6);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }
}