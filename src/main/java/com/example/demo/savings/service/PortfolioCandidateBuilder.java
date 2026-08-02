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

    private static final int BASE_SAVING_SLOTS = 3;  // 기본 권장 개수
    private static final int MAX_SAVING_SLOTS = 5;   // 고금리/고예산 유저 최대 확장 개수
    private static final double HIGH_RATE_THRESHOLD = 0.06; // 초고금리 기준 (6.0%)

    public PortfolioCandidate buildGreedyCandidate(
            String optionType,
            List<AllocationSlot> slots,
            List<ProductEvaluation> evaluations,
            UserProfile profile,
            double riskTolerance
    ) {
        List<PortfolioAllocation> selected = new ArrayList<>();
        Set<String> selectedProductIds = new HashSet<>();

        // 유저 자산 잔여 여력 tracking
        long totalMonthlySavingBudget = profile.monthlySaving();
        long remainingMonthlySaving = totalMonthlySavingBudget;
        long remainingLumpSum = Math.max(0L, profile.lumpSum() - profile.emergencyFund());
        int targetMonths = profile.targetMonths() > 0 ? profile.targetMonths() : 12;

        // 최소 실익 금액 허들 (유저 월 저축액의 10% 미만 단속, 최소 3만 원)
        long minMeaningfulMonthlyAmount = Math.max(30_000L, (long) (totalMonthlySavingBudget * 0.10));

        // ==========================================
        // 🎯 1. 목돈(예금) 자율 할당 (예금자보호 5,000만 원 한도)
        // ==========================================
        if (remainingLumpSum > 0) {
            Optional<ProductEvaluation> depositOpt = findBestProduct(evaluations, ProductType.DEPOSIT, selectedProductIds, profile, optionType, selected, 0, targetMonths);

            if (depositOpt.isPresent()) {
                ProductEvaluation deposit = depositOpt.get();
                long maxProductLimit = deposit.product().maximumAmount() == 0 ? Long.MAX_VALUE : deposit.product().maximumAmount();
                long allocAmount = Math.min(remainingLumpSum, Math.min(maxProductLimit, 50_000_000L));
                allocAmount = Math.max(deposit.product().minimumAmount(), allocAmount);

                AllocationSlot depositSlot = new AllocationSlot(AllocationType.LUMP_SUM, allocAmount, deposit.product().termMonths(), 0);
                selected.add(new PortfolioAllocation(selected.size(), depositSlot, deposit));
                selectedProductIds.add(deposit.product().productId());
            }
        }

        // ==========================================
        // 🎯 2. 월 적금 동적 배분 (기본 3개 ~ 동적 조건 시 최대 5개)
        // ==========================================
        int savingCount = 0;
        int slotIndex = 0;

        while (remainingMonthlySaving > 0) {
            int currentMaxAllowedSlots = calculateDynamicSlotLimit(
                    savingCount,
                    remainingMonthlySaving,
                    totalMonthlySavingBudget,
                    evaluations,
                    selectedProductIds
            );

            if (savingCount >= currentMaxAllowedSlots) {
                break;
            }

            Optional<ProductEvaluation> bestSavingOpt = findBestProduct(evaluations, ProductType.SAVING, selectedProductIds, profile, optionType, selected, slotIndex, targetMonths);

            if (bestSavingOpt.isEmpty()) {
                break;
            }

            ProductEvaluation savingEval = bestSavingOpt.get();
            int productTermMonths = savingEval.product().termMonths();
            long productMax = savingEval.product().maximumAmount() == 0 ? Long.MAX_VALUE : savingEval.product().maximumAmount();
            long optimalMonthlyAlloc = calculateOptimalMonthlyAmount(savingEval, remainingMonthlySaving, profile, optionType);
            long actualMonthlyAlloc = Math.min(remainingMonthlySaving, Math.min(productMax, optimalMonthlyAlloc));

            // 최소 실익 금액 미만 체크
            if (actualMonthlyAlloc < Math.max(savingEval.product().minimumAmount(), minMeaningfulAmount(savingCount, minMeaningfulMonthlyAmount))) {
                break;
            }

            int startMonth = calculateStartMonth(optionType, AllocationType.MONTHLY_SAVING, slotIndex);

            // 💡 [핵심 수정 1] 총 납입액 = 월 납입액 * 상품 실제 기간(productTermMonths) (targetMonths 사용 금지!)
            AllocationSlot candidateSlot = new AllocationSlot(
                    AllocationType.MONTHLY_SAVING,
                    actualMonthlyAlloc * productTermMonths,
                    productTermMonths,
                    startMonth
            );

            // 사전 타임라인 검증
            if (isFeasibleWith(selected, selected.size(), candidateSlot, savingEval, profile, optionType)) {
                selected.add(new PortfolioAllocation(selected.size(), candidateSlot, savingEval));
                selectedProductIds.add(savingEval.product().productId());
                remainingMonthlySaving -= actualMonthlyAlloc;
                savingCount++;
            }

            slotIndex++;
        }

        // ==========================================
        // 🎯 3. 잔여 월 저축액 백필
        // ==========================================
        if (remainingMonthlySaving > 0 && !selected.isEmpty()) {
            backfillToSelected(selected, remainingMonthlySaving, profile, optionType);
        }

        return buildCandidate(selected, optionType);
    }

    private int calculateDynamicSlotLimit(
            int currentSavingCount,
            long remainingMonthlySaving,
            long totalMonthlyBudget,
            List<ProductEvaluation> evaluations,
            Set<String> selectedProductIds
    ) {
        if (currentSavingCount < BASE_SAVING_SLOTS) {
            return BASE_SAVING_SLOTS;
        }

        boolean hasHighRateProduct = evaluations.stream()
                .filter(e -> e.product() != null && e.product().productType() == ProductType.SAVING)
                .filter(e -> !selectedProductIds.contains(e.product().productId()))
                .anyMatch(e -> e.expectedRate() >= HIGH_RATE_THRESHOLD || e.product().maxRate() >= HIGH_RATE_THRESHOLD);

        boolean hasSubstantialRemainingBudget = (double) remainingMonthlySaving / totalMonthlyBudget >= 0.20;

        if (hasHighRateProduct || hasSubstantialRemainingBudget) {
            return MAX_SAVING_SLOTS;
        }

        return BASE_SAVING_SLOTS;
    }

    private long minMeaningfulAmount(int currentCount, long minThreshold) {
        return currentCount == 0 ? 0L : minThreshold;
    }

    private void backfillToSelected(
            List<PortfolioAllocation> selected,
            long remainingMonthlySaving,
            UserProfile profile,
            String optionType
    ) {
        for (int i = 0; i < selected.size(); i++) {
            PortfolioAllocation alloc = selected.get(i);
            if (alloc.slot().allocationType() != AllocationType.MONTHLY_SAVING) continue;

            ProductEvaluation eval = alloc.product();
            int termMonths = eval.product().termMonths();
            long currentMonthly = monthlyAmount(alloc.slot());
            long productMaxMonthly = eval.product().maximumAmount() == 0 ? Long.MAX_VALUE : eval.product().maximumAmount();
            long margin = productMaxMonthly - currentMonthly;

            if (margin > 0) {
                long additionalMonthly = Math.min(remainingMonthlySaving, margin);
                long newTotalMonthly = currentMonthly + additionalMonthly;

                // 💡 [핵심 수정 2] 백필 시에도 targetMonths가 아니라 상품의 termMonths 기준으로 생성
                AllocationSlot upgradedSlot = new AllocationSlot(
                        AllocationType.MONTHLY_SAVING,
                        newTotalMonthly * termMonths,
                        termMonths,
                        alloc.slot().startMonth()
                );

                List<PortfolioAllocation> tentative = new ArrayList<>(selected);
                tentative.set(i, new PortfolioAllocation(alloc.slotIndex(), upgradedSlot, eval));

                if (isFeasible(buildCandidate(tentative, optionType), profile)) {
                    selected.set(i, new PortfolioAllocation(alloc.slotIndex(), upgradedSlot, eval));
                    remainingMonthlySaving -= additionalMonthly;
                }

                if (remainingMonthlySaving <= 0) break;
            }
        }
    }

    private Optional<ProductEvaluation> findBestProduct(
            List<ProductEvaluation> evaluations,
            ProductType productType,
            Set<String> selectedProductIds,
            UserProfile profile,
            String optionType,
            List<PortfolioAllocation> currentSelected,
            int slotIndex,
            int targetMonths
    ) {
        Comparator<ProductEvaluation> ranking = Comparator
                .comparingDouble((ProductEvaluation evaluation) -> greedyScore(evaluation, optionType, profile))
                .reversed()
                .thenComparing(evaluation -> evaluation.product().productId());

        return evaluations.stream()
                .filter(eval -> eval.product() != null && eval.product().productType() == productType)
                .filter(eval -> eval.product().termMonths() <= targetMonths)
                .filter(eval -> !selectedProductIds.contains(eval.product().productId()))
                .sorted(ranking)
                .findFirst();
    }

    private long calculateOptimalMonthlyAmount(ProductEvaluation eval, long remainingMonthly, UserProfile profile, String optionType) {
        SavingsProduct product = eval.product();
        long productMax = product.maximumAmount() == 0 ? Long.MAX_VALUE : product.maximumAmount();

        if ("AGGRESSIVE".equals(optionType)) {
            return Math.max(product.minimumAmount(), Math.min(remainingMonthly, productMax));
        }

        if ("STABLE".equals(optionType)) {
            long halfAmount = (long) (remainingMonthly * 0.5);
            return Math.max(product.minimumAmount(), Math.min(halfAmount, productMax));
        }

        long cardDemand = eval.resourceDemand() != null ? eval.resourceDemand().cardBudget() : 0L;
        if (cardDemand > 0 && cardDemand <= profile.cardBudgetCap()) {
            return Math.max(product.minimumAmount(), Math.min(remainingMonthly, cardDemand));
        }
        return Math.max(product.minimumAmount(), Math.min(remainingMonthly, productMax));
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
            UserProfile profile,
            String optionType
    ) {
        List<PortfolioAllocation> tentative = new ArrayList<>(selected);
        tentative.add(new PortfolioAllocation(slotIndex, slot, evaluation));
        return isFeasible(buildCandidate(tentative, optionType), profile);
    }

    private double greedyScore(ProductEvaluation evaluation, String optionType, UserProfile profile) {
        SavingsProduct product = evaluation.product();
        if (product == null) return 0.0;

        double baseRate = product.baseRate();
        double maxRate = product.maxRate();
        double expectedRate = evaluation.expectedRate();
        double risk = evaluation.rateRisk();

        switch (optionType) {
            case "STABLE":
                return baseRate * 2.0 - (risk * 3.0);
            case "BALANCED":
                long cardDemand = evaluation.resourceDemand() != null ? evaluation.resourceDemand().cardBudget() : 0L;
                double cardBonus = (cardDemand > 0 && cardDemand <= profile.cardBudgetCap()) ? 0.3 : 0.0;
                return expectedRate + cardBonus - (risk * 0.5);
            case "AGGRESSIVE":
                return maxRate * 100.0;
            default:
                return expectedRate;
        }
    }

    public PortfolioCandidate buildCandidate(List<PortfolioAllocation> allocations) {
        return buildCandidate(allocations, "BALANCED");
    }

    public PortfolioCandidate buildCandidate(List<PortfolioAllocation> allocations, String optionType) {
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

            double appliedRate = "AGGRESSIVE".equals(optionType)
                    ? product.product().maxRate()
                    : product.expectedRate();

            expectedReturn += slot.amount() * appliedRate * durationFactor;
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

        // 💡 [핵심 수정 3] RoadmapEngine과 100% 동일한 월별 타임라인 검증 수식 적용
        Map<Integer, Long> monthlyExpenseByMonth = new HashMap<>();

        int lastStartMonth = candidate.allocations().stream()
                .mapToInt(a -> a.slot().startMonth())
                .max()
                .orElse(0);
        int horizonMonths = profile.targetMonths() + lastStartMonth;

        for (PortfolioAllocation alloc : candidate.allocations()) {
            SavingsProduct product = alloc.product().product();
            if (product.productType() == ProductType.SAVING) {
                // RoadmapEngine과 동일하게 amount / termMonths 로 월 납입금 계산
                long mAmount = monthlyAmount(alloc.slot().amount(), product.termMonths());
                int startMonth = alloc.slot().startMonth();
                int endMonth = startMonth + product.termMonths();

                for (int m = startMonth; m < endMonth && m < horizonMonths; m++) {
                    monthlyExpenseByMonth.merge(m, mAmount, Long::sum);
                }
            }
        }

        for (Long monthExpense : monthlyExpenseByMonth.values()) {
            if (monthExpense > profile.monthlySaving() + 1000L) {
                return false;
            }
        }

        if (candidate.lumpSumPrincipal() > allocatable
                || candidate.cardBudgetUsed() > profile.cardBudgetCap()
                || candidate.salaryTransferUsed() > (profile.salaryTransferable() ? 1 : 0)
                || candidate.cashBalanceUsed() > allocatable
                || candidate.allocations().stream().anyMatch(a -> !a.product().hardRequirementsSatisfied())) {
            return false;
        }

        return candidate.firstTradeUsed().values().stream().allMatch(count -> count <= 1);
    }

    // RoadmapEngine과 수식 100% 동일화
    private long monthlyAmount(long totalContribution, int termMonths) {
        if (termMonths <= 0) return totalContribution;
        return totalContribution / termMonths;
    }

    public long monthlyAmount(AllocationSlot slot) {
        return monthlyAmount(slot.amount(), slot.termMonths());
    }
}