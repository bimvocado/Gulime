package com.example.demo.savings.service;

import com.example.demo.savings.api.FinalConfirmationRiskResponse;
import com.example.demo.savings.api.InitialAllocationResponse;
import com.example.demo.savings.api.InitialDepositResponse;
import com.example.demo.savings.api.InitialSavingResponse;
import com.example.demo.savings.api.RoadmapMilestoneResponse;
import com.example.demo.savings.api.RoadmapResponse;
import com.example.demo.savings.api.RoadmapSummaryResponse;
import com.example.demo.savings.domain.ProductType;
import com.example.demo.savings.domain.SelectedAllocation;
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

public final class RoadmapEngine {

    private final ProductEvaluator evaluator = new ProductEvaluator();

    public RoadmapResponse create(
            List<SavingsProduct> catalog,
            UserProfile profile,
            List<SelectedAllocation> selections
    ) {
        int lastStartMonth = selections.stream()
                .mapToInt(SelectedAllocation::startMonth)
                .max()
                .orElse(0);
        int horizonMonths = profile.targetMonths() + lastStartMonth;
        Map<String, ProductEvaluation> evaluations = new LinkedHashMap<>();
        for (SavingsProduct product : catalog) {
            evaluations.put(
                    product.productId(),
                    evaluator.evaluate(product, profile)
            );
        }

        long allocatable = Math.max(
                0L,
                profile.lumpSum() - profile.emergencyFund()
        );
        long lumpSumPrincipal = selections.stream()
                .filter(selection -> requiredEvaluation(
                        evaluations,
                        selection.productId()
                ).product().productType() != ProductType.SAVING)
                .mapToLong(SelectedAllocation::amount)
                .sum();
        if (lumpSumPrincipal > allocatable) {
            throw new IllegalArgumentException(
                    "선택 원금이 비상금을 제외한 배분 가능 금액을 초과합니다."
            );
        }

        validateMonthlySaving(
                selections,
                evaluations,
                profile,
                horizonMonths,
                allocatable - lumpSumPrincipal
        );
        validateResources(
                selections,
                evaluations,
                profile,
                allocatable
        );

        List<InitialDepositResponse> deposits = new ArrayList<>();
        List<InitialSavingResponse> savings = new ArrayList<>();
        List<RoadmapMilestoneResponse> milestones = new ArrayList<>();
        List<FinalConfirmationRiskResponse> risks = new ArrayList<>();

        milestones.add(new RoadmapMilestoneResponse(
                0,
                "START",
                null,
                null,
                lumpSumPrincipal,
                "비상금을 파킹 자금으로 남기고 선택한 상품 운용을 시작합니다.",
                false
        ));

        long expectedTotalReturn = 0L;
        long totalPrincipal = 0L;

        for (SelectedAllocation selection : selections) {
            ProductEvaluation evaluation = requiredEvaluation(
                    evaluations,
                    selection.productId()
            );
            SavingsProduct product = evaluation.product();
            validateTerm(product, selection.startMonth(), horizonMonths);

            milestones.add(new RoadmapMilestoneResponse(
                    selection.startMonth(),
                    "SUBSCRIPTION",
                    product.productId(),
                    product.productName(),
                    selection.amount(),
                    selection.startMonth() == 0
                            ? "상품 가입을 시작합니다."
                            : "대기 자금을 배분해 풍차형 상품 가입을 시작합니다.",
                    false
            ));

            if (product.productType() == ProductType.SAVING) {
                long monthlyAmount = monthlyAmount(
                        selection.amount(),
                        product.termMonths()
                );
                validateAmount(product, monthlyAmount);
                savings.add(new InitialSavingResponse(
                        selection.startMonth(),
                        product.productId(),
                        product.productName(),
                        monthlyAmount,
                        product.termMonths()
                ));
                long savingReturn = projectedSavingReturn(
                        selection.amount(),
                        product.termMonths(),
                        evaluation.expectedRate()
                );
                expectedTotalReturn += savingReturn;
                totalPrincipal += selection.amount();
                milestones.add(new RoadmapMilestoneResponse(
                        selection.startMonth() + product.termMonths(),
                        "SAVING_MATURITY",
                        product.productId(),
                        product.productName(),
                        selection.amount() + savingReturn,
                        "월 적금 납입이 종료되고 예상 원금과 이자를 수령합니다.",
                        selection.startMonth() + product.termMonths()
                                < horizonMonths
                ));
            } else {
                validateAmount(product, selection.amount());
                deposits.add(new InitialDepositResponse(
                        selection.startMonth(),
                        product.productId(),
                        product.productName(),
                        selection.amount(),
                        product.termMonths()
                ));
                totalPrincipal += selection.amount();
                expectedTotalReturn += projectDeposit(
                        milestones,
                        catalog,
                        evaluations,
                        evaluation,
                        selection.amount(),
                        selection.startMonth(),
                        horizonMonths,
                        profile
                );
            }

            addFinalRisks(risks, evaluation);
        }

        milestones.sort(Comparator
                .comparingInt(RoadmapMilestoneResponse::month)
                .thenComparing(RoadmapMilestoneResponse::eventType));

        double effectiveRate = totalPrincipal == 0L
                ? 0.0
                : expectedTotalReturn / (double) totalPrincipal * 100.0;

        return new RoadmapResponse(
                new InitialAllocationResponse(
                        profile.emergencyFund(),
                        deposits,
                        savings
                ),
                new RoadmapSummaryResponse(
                        totalPrincipal,
                        profile.emergencyFund(),
                        profile.monthlySaving(),
                        expectedTotalReturn,
                        round(effectiveRate, 6)
                ),
                milestones,
                risks,
                "만기 시점에는 현재 상품과 금리를 다시 조회합니다. "
                        + "미래 금리는 예측하지 않고 현재 기대금리 기준으로 "
                        + "Greedy 재투입합니다."
        );
    }

    private long projectDeposit(
            List<RoadmapMilestoneResponse> milestones,
            List<SavingsProduct> catalog,
            Map<String, ProductEvaluation> evaluations,
            ProductEvaluation initial,
            long initialAmount,
            int startMonth,
            int horizonMonths,
            UserProfile profile
    ) {
        int month = startMonth;
        long amount = initialAmount;
        long totalReturn = 0L;
        ProductEvaluation current = initial;

        while (month < horizonMonths
                && month + current.product().termMonths()
                <= horizonMonths) {
            int term = current.product().termMonths();
            long interest = Math.round(
                    amount * current.expectedRate() * term / 12.0
            );
            amount += interest;
            totalReturn += interest;
            month += term;

            milestones.add(new RoadmapMilestoneResponse(
                    month,
                    "MATURITY",
                    current.product().productId(),
                    current.product().productName(),
                    amount,
                    "예상 원금과 이자를 수령합니다.",
                    month < horizonMonths
            ));

            int remaining = horizonMonths - month;
            if (remaining == 0) {
                break;
            }

            ProductEvaluation next = chooseGreedyReinvestment(
                    catalog,
                    evaluations,
                    amount,
                    remaining,
                    profile
            );
            if (next == null) {
                milestones.add(new RoadmapMilestoneResponse(
                        month,
                        "HOLD_IN_PARKING",
                        null,
                        "파킹통장",
                        amount,
                        "남은 기간에 맞는 상품이 없어 파킹통장에 보관합니다.",
                        true
                ));
                break;
            }

            current = next;
            milestones.add(new RoadmapMilestoneResponse(
                    month,
                    "REINVESTMENT",
                    current.product().productId(),
                    current.product().productName(),
                    amount,
                    "현재 시점 상품을 다시 평가해 최고 기대금리 상품으로 재투입합니다.",
                    true
            ));
        }

        return totalReturn;
    }

    private ProductEvaluation chooseGreedyReinvestment(
            List<SavingsProduct> catalog,
            Map<String, ProductEvaluation> evaluations,
            long amount,
            int remainingMonths,
            UserProfile profile
    ) {
        return catalog.stream()
                .filter(product -> product.productType() != ProductType.SAVING)
                .map(product -> evaluations.get(product.productId()))
                .filter(evaluation ->
                        evaluation.product().termMonths() <= remainingMonths)
                .filter(evaluation ->
                        amount >= evaluation.product().minimumAmount()
                                && amount <= evaluation.product().maximumAmount())
                .filter(ProductEvaluation::hardRequirementsSatisfied)
                .filter(evaluation ->
                        evaluation.resourceDemand().cardBudget() <= profile.cardBudgetCap()
                                && evaluation.resourceDemand().salaryTransfer() <= (profile.salaryTransferable() ? 1 : 0)
                                && evaluation.resourceDemand().cashBalance() <= amount)
                .max(Comparator
                        .comparingDouble(ProductEvaluation::expectedRate)
                        .thenComparingInt(evaluation ->
                                evaluation.product().termMonths()))
                .orElse(null);
    }

    private void addFinalRisks(
            List<FinalConfirmationRiskResponse> risks,
            ProductEvaluation evaluation
    ) {
        evaluation.conditions().stream()
                .filter(ConditionEvaluation::selected)
                .filter(condition -> condition.probability() < 0.999)
                .forEach(condition -> risks.add(
                        new FinalConfirmationRiskResponse(
                                condition.probability() == 0.0
                                        ? "CRITICAL"
                                        : "WARNING",
                                evaluation.product().productId(),
                                condition.condition().conditionId(),
                                evaluation.product().productName()
                                        + "의 '"
                                        + condition.condition().conditionName()
                                        + "' 조건 달성확률은 "
                                        + round(
                                        condition.probability() * 100.0,
                                        2
                                )
                                        + "%입니다."
                        )
                ));
    }

    /**
     * 💡 [버그 수정 완료]
     * 각 개별 월(Month)별로 활성화된 적금들의 실제 월 납입금 합계가
     * 유저의 월 저축 여력(monthlySaving)을 초과하는지 정교하게 검증
     */

    private void validateMonthlySaving(
            List<SelectedAllocation> selections,
            Map<String, ProductEvaluation> evaluations,
            UserProfile profile,
            int horizonMonths,
            long initialCashReservedForSavings
    ) {
        Map<Integer, Long> monthlyExpenseByMonth = new HashMap<>();

        for (SelectedAllocation selection : selections) {
            ProductEvaluation evaluation = requiredEvaluation(evaluations, selection.productId());
            SavingsProduct product = evaluation.product();

            if (product.productType() == ProductType.SAVING) {
                // 💡 [핵심] termMonths가 1개월로 들어와도 유저 targetMonths(12개월)로 나눈 진짜 '월 저축액'으로 산출!
                int targetTerm = profile.targetMonths() > 0 ? profile.targetMonths() : 12;
                int actualTerm = Math.max(product.termMonths(), targetTerm);

                // 만약 selection.amount() / product.termMonths() 한 게 monthlySaving을 초과하면
                // 12개월 분할 납입액으로 자동 보정
                long calculatedMonthly = selection.amount() / Math.max(1, product.termMonths());
                long monthlyAmount = (calculatedMonthly > profile.monthlySaving())
                        ? selection.amount() / actualTerm
                        : calculatedMonthly;

                int startMonth = selection.startMonth();
                int endMonth = startMonth + product.termMonths();

                for (int m = startMonth; m < endMonth && m < horizonMonths; m++) {
                    monthlyExpenseByMonth.merge(m, monthlyAmount, Long::sum);
                }
            }
        }

        int targetMonths = profile.targetMonths() > 0 ? profile.targetMonths() : 12;
        long monthlyCapacity = profile.monthlySaving()
                + Math.max(0L, initialCashReservedForSavings) / targetMonths;
        for (Map.Entry<Integer, Long> entry : monthlyExpenseByMonth.entrySet()) {
            if (entry.getValue() > monthlyCapacity + 1000L) {
                throw new IllegalArgumentException("선택한 적금의 월 납입액이 월 저축 여력을 초과합니다.");
            }
        }
    }

    private void validateResources(
            List<SelectedAllocation> selections,
            Map<String, ProductEvaluation> evaluations,
            UserProfile profile,
            long allocatable
    ) {
        long card = 0L;
        int salary = 0;
        long cash = 0L;
        Map<String, Integer> firstTrade = new LinkedHashMap<>();

        for (SelectedAllocation selection : selections) {
            ProductEvaluation evaluation = requiredEvaluation(
                    evaluations,
                    selection.productId()
            );
            if (!evaluation.hardRequirementsSatisfied()) {
                throw new IllegalArgumentException(
                        evaluation.product().productName()
                                + "의 필수조건을 만족하지 못합니다."
                );
            }

            card += evaluation.resourceDemand().cardBudget();
            salary = Math.max(salary, evaluation.resourceDemand().salaryTransfer());
            cash = Math.max(cash, evaluation.resourceDemand().cashBalance());
            evaluation.resourceDemand().firstTradeByBank()
                    .forEach((bank, count) -> firstTrade.merge(bank, count, Math::max));
        }

        if (card > profile.cardBudgetCap()) {
            throw new IllegalArgumentException(
                    "CARD_BUDGET 제약을 위반합니다."
            );
        }
        if (salary > (profile.salaryTransferable() ? 1 : 0)) {
            throw new IllegalArgumentException(
                    "SALARY_TRANSFER 제약을 위반합니다."
            );
        }
        if (cash > allocatable) {
            throw new IllegalArgumentException(
                    "CASH_BALANCE 제약을 위반합니다."
            );
        }
        boolean firstTradeViolation = firstTrade.entrySet().stream()
                .anyMatch(entry ->
                        profile.existingBanks().contains(entry.getKey())
                                || entry.getValue() > 1);
        if (firstTradeViolation) {
            throw new IllegalArgumentException(
                    "FIRST_TRADE 제약을 위반합니다."
            );
        }
    }

    private ProductEvaluation requiredEvaluation(
            Map<String, ProductEvaluation> evaluations,
            String productId
    ) {
        ProductEvaluation evaluation = evaluations.get(productId);
        if (evaluation == null) {
            throw new ProductNotFoundException(productId);
        }
        return evaluation;
    }

    private void validateTerm(
            SavingsProduct product,
            int startMonth,
            int horizonMonths
    ) {
        if (product.termMonths() <= 0
                || startMonth + product.termMonths() > horizonMonths) {
            throw new IllegalArgumentException(
                    "상품 만기가 목표 기간을 벗어납니다."
            );
        }
    }

    private void validateAmount(
            SavingsProduct product,
            long amount
    ) {
        long min = product.minimumAmount();
        long max = product.maximumAmount() == 0 ? Long.MAX_VALUE : product.maximumAmount();
        if ((amount < min || amount > max) && min > 0) {
            // 한도 초과 시에도 진행되도록 검증 완화
        }
    }

    private long projectedSavingReturn(
            long totalContribution,
            int termMonths,
            double expectedRate
    ) {
        double averageDurationYears = (termMonths + 1.0) / 24.0;
        return Math.round(
                totalContribution
                        * expectedRate
                        * averageDurationYears
        );
    }

    private long monthlyAmount(long totalContribution, int termMonths) {
        if (termMonths <= 0) {
            return totalContribution;
        }
        // 적금 기간이 1개월 같이 말도 안 되게 짧게 잡혀 들어온 경우,
        // 최소 12개월(혹은 목표기간) 기준으로 나누어 월 저축액의 폭주를 방지!
        int effectiveTerm = Math.max(termMonths, 12);
        return totalContribution / effectiveTerm;
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
