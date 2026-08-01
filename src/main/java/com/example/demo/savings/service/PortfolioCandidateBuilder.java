package com.example.demo.savings.service;

import com.example.demo.savings.domain.AllocationSlot;
import com.example.demo.savings.domain.AllocationType;
import com.example.demo.savings.domain.ProductType;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.UserProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PortfolioCandidateBuilder {

    public PortfolioCandidate buildGreedyCandidate(
            String optionType,
            List<AllocationSlot> slots, // 외부 슬롯 인자는 구조 유지를 위해 받되 내부에서 유저 예산으로 재구성
            List<ProductEvaluation> evaluations,
            UserProfile profile,
            double riskTolerance
    ) {
        List<PortfolioAllocation> selected = new ArrayList<>();
        Set<String> selectedProductIds = new HashSet<>();

        // 유저 자산 잔여 여력 tracking
        long remainingMonthlySaving = profile.monthlySaving();
        long remainingLumpSum = Math.max(0L, profile.lumpSum() - profile.emergencyFund());
        int targetMonths = profile.targetMonths() > 0 ? profile.targetMonths() : 12;

        // ==========================================
        // 🎯 1. 목돈(예금) 자율 할당 (예금자보호 5,000만 원 한도)
        // ==========================================
        if (remainingLumpSum > 0) {
            Optional<ProductEvaluation> depositOpt = findBestProduct(evaluations, ProductType.DEPOSIT, selectedProductIds, profile, optionType, selected, 0);

            if (depositOpt.isPresent()) {
                ProductEvaluation deposit = depositOpt.get();
                long maxProductLimit = deposit.product().maximumAmount() == 0 ? Long.MAX_VALUE : deposit.product().maximumAmount();
                long allocAmount = Math.min(remainingLumpSum, Math.min(maxProductLimit, 50_000_000L));
                allocAmount = Math.max(deposit.product().minimumAmount(), allocAmount);

                AllocationSlot depositSlot = new AllocationSlot(AllocationType.LUMP_SUM, allocAmount, targetMonths, 0);
                selected.add(new PortfolioAllocation(selected.size(), depositSlot, deposit));
                selectedProductIds.add(deposit.product().productId());
            }
        }

        // ==========================================
        // 🎯 2. 월 적금 Primary (1순위 고금리 상품)
        // ==========================================
        if (remainingMonthlySaving > 0) {
            Optional<ProductEvaluation> primaryOpt = findBestProduct(evaluations, ProductType.SAVING, selectedProductIds, profile, optionType, selected, 0);

            if (primaryOpt.isPresent()) {
                ProductEvaluation primary = primaryOpt.get();

                // 1등 적금의 우대금리 최적 한도 산출
                long primaryMonthlyAlloc = calculateOptimalMonthlyAmount(primary, remainingMonthlySaving, profile);
                int primaryStartMonth = calculateStartMonth(optionType, AllocationType.MONTHLY_SAVING, 0);

                AllocationSlot primarySlot = new AllocationSlot(
                        AllocationType.MONTHLY_SAVING,
                        primaryMonthlyAlloc * targetMonths,
                        targetMonths,
                        primaryStartMonth
                );

                selected.add(new PortfolioAllocation(selected.size(), primarySlot, primary));
                selectedProductIds.add(primary.product().productId());

                remainingMonthlySaving -= primaryMonthlyAlloc; // 잔여 월 저축액 차감

                // ==========================================
                // 🎯 3. 월 적금 Secondary (2순위) OR 몰빵(Back-fill)
                // ==========================================
                if (remainingMonthlySaving > 0) {
                    Optional<ProductEvaluation> secondaryOpt = findBestProduct(evaluations, ProductType.SAVING, selectedProductIds, profile, optionType, selected, 1);

                    if (secondaryOpt.isPresent()) {
                        ProductEvaluation secondary = secondaryOpt.get();
                        int secondaryStartMonth = calculateStartMonth(optionType, AllocationType.MONTHLY_SAVING, 1);

                        // 💡 [초과 방지 핵심 로직]
                        // 만약 1등과 2등 적금의 시작월이 같다면(예: AGGRESSIVE는 둘 다 0월차 시작),
                        // 2등 적금의 월 납입액은 (전체 월 저축 여력 - 1등 월 납입액)을 절대 넘을 수 없도록 상한선 지정!
                        long maxAllocableMonthly = remainingMonthlySaving;
                        long secondaryProductMax = secondary.product().maximumAmount() == 0 ? Long.MAX_VALUE : secondary.product().maximumAmount();
                        long secondaryMonthlyAlloc = Math.min(maxAllocableMonthly, secondaryProductMax);

                        // 최소 가입금액을 충족할 때만 2등 상품으로 추가
                        if (secondaryMonthlyAlloc >= secondary.product().minimumAmount()) {
                            AllocationSlot secondarySlot = new AllocationSlot(
                                    AllocationType.MONTHLY_SAVING,
                                    secondaryMonthlyAlloc * targetMonths,
                                    targetMonths,
                                    secondaryStartMonth
                            );
                            selected.add(new PortfolioAllocation(selected.size(), secondarySlot, secondary));
                        } else {
                            // 최소 금액 미달 시 1등 상품으로 몰빵 (Back-fill)
                            backfillToPrimary(selected, primary, remainingMonthlySaving, targetMonths, primaryStartMonth, primaryMonthlyAlloc);
                        }
                    } else {
                        // 2등 상품 탐색 실패 시 1등 상품으로 몰빵 (Back-fill)
                        backfillToPrimary(selected, primary, remainingMonthlySaving, targetMonths, primaryStartMonth, primaryMonthlyAlloc);
                    }
                }
            }
        }

        return buildCandidate(selected);
    }

    /**
     * 1등 상품으로 잔여 여력을 몰아주는 Back-fill 헬퍼 메서드
     */
    private void backfillToPrimary(
            List<PortfolioAllocation> selected,
            ProductEvaluation primary,
            long remainingMonthlySaving,
            int targetMonths,
            int primaryStartMonth,
            long primaryMonthlyAlloc
    ) {
        long primaryMaxMonthly = primary.product().maximumAmount() == 0 ? Long.MAX_VALUE : primary.product().maximumAmount();
        long additionalMonthly = Math.min(remainingMonthlySaving, primaryMaxMonthly - primaryMonthlyAlloc);

        if (additionalMonthly > 0) {
            long newTotalAmount = (primaryMonthlyAlloc + additionalMonthly) * targetMonths;
            AllocationSlot upgradedSlot = new AllocationSlot(
                    AllocationType.MONTHLY_SAVING,
                    newTotalAmount,
                    targetMonths,
                    primaryStartMonth
            );

            int primaryIndex = selected.size() - 1;
            selected.set(primaryIndex, new PortfolioAllocation(selected.get(primaryIndex).slotIndex(), upgradedSlot, primary));
        }
    }

    /**
     * 조건 검증을 통과한 최적 상품 탐색
     */
    private Optional<ProductEvaluation> findBestProduct(
            List<ProductEvaluation> evaluations,
            ProductType productType,
            Set<String> selectedProductIds,
            UserProfile profile,
            String optionType,
            List<PortfolioAllocation> currentSelected,
            int slotIndex
    ) {
        Comparator<ProductEvaluation> ranking = Comparator
                .comparingDouble((ProductEvaluation evaluation) -> greedyScore(evaluation, optionType, profile))
                .reversed()
                .thenComparing(evaluation -> evaluation.product().productId());

        return evaluations.stream()
                .filter(eval -> eval.product() != null && eval.product().productType() == productType)
                .filter(eval -> !selectedProductIds.contains(eval.product().productId()))
                .sorted(ranking)
                .filter(eval -> isFeasibleWith(currentSelected, slotIndex, new AllocationSlot(
                        productType == ProductType.SAVING ? AllocationType.MONTHLY_SAVING : AllocationType.LUMP_SUM,
                        100_000L, profile.targetMonths(), 0), eval, profile))
                .findFirst();
    }

    /**
     * 카드 실적/한도를 고려한 최적 월 납입금 산출
     */
    private long calculateOptimalMonthlyAmount(ProductEvaluation eval, long remainingMonthly, UserProfile profile) {
        SavingsProduct product = eval.product();
        long productMax = product.maximumAmount() == 0 ? Long.MAX_VALUE : product.maximumAmount();
        long cardDemand = eval.resourceDemand() != null ? eval.resourceDemand().cardBudget() : 0L;

        long effectiveMonthly = remainingMonthly;

        if (cardDemand > 0 && cardDemand <= profile.cardBudgetCap()) {
            effectiveMonthly = Math.min(remainingMonthly, cardDemand);
        } else if (productMax != Long.MAX_VALUE) {
            effectiveMonthly = Math.min(remainingMonthly, productMax);
        } else {
            effectiveMonthly = Math.min(remainingMonthly, (long) (profile.monthlySaving() * 0.5));
        }

        return Math.max(product.minimumAmount(), effectiveMonthly);
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

    private boolean isFeasibleWith(
            List<PortfolioAllocation> selected,
            int slotIndex,
            AllocationSlot slot,
            ProductEvaluation evaluation,
            UserProfile profile
    ) {
        List<PortfolioAllocation> tentative = new ArrayList<>(selected);
        tentative.add(new PortfolioAllocation(slotIndex, slot, evaluation));
        return isFeasible(buildCandidate(tentative), profile);
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

        long totalMonthlySavingDemand = candidate.allocations().stream()
                .filter(allocation -> allocation.slot().allocationType() == AllocationType.MONTHLY_SAVING)
                .mapToLong(allocation -> monthlyAmount(allocation.slot()))
                .sum();

        if (candidate.lumpSumPrincipal() > allocatable
                || totalMonthlySavingDemand > profile.monthlySaving() + 1000L
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