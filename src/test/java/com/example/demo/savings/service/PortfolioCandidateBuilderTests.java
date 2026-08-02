package com.example.demo.savings.service;

import com.example.demo.savings.domain.AllocationType;
import com.example.demo.savings.domain.EmploymentType;
import com.example.demo.savings.domain.ProductType;
import com.example.demo.savings.domain.SelectedAllocation;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioCandidateBuilderTests {

    private final PortfolioCandidateBuilder builder = new PortfolioCandidateBuilder();

    @Test
    void fillsHigherReturnDepositToItsLimitBeforeUsingNextProduct() {
        ProductEvaluation highReturn = evaluation(product(
                "HIGH",
                ProductType.DEPOSIT,
                100_000L,
                300_000L,
                0.05
        ));
        ProductEvaluation lowerReturn = evaluation(product(
                "LOW",
                ProductType.DEPOSIT,
                100_000L,
                1_000_000L,
                0.03
        ));

        PortfolioCandidate candidate = builder.buildGreedyCandidate(
                "BALANCED",
                List.of(),
                List.of(lowerReturn, highReturn),
                profile(1_000_000L, 0L),
                0.5
        );

        assertThat(candidate.allocations())
                .extracting(allocation -> allocation.product().product().productId())
                .containsExactly("HIGH", "LOW");
        assertThat(candidate.allocations())
                .extracting(allocation -> allocation.slot().amount())
                .containsExactly(300_000L, 700_000L);
        assertThat(candidate.lumpSumPrincipal()).isEqualTo(1_000_000L);
    }

    @Test
    void optimizesMonthlyAmountsInsteadOfSplittingMonthlyBudgetEqually() {
        ProductEvaluation highReturn = evaluation(product(
                "HIGH_SAVING",
                ProductType.SAVING,
                10_000L,
                200_000L,
                0.06
        ));
        ProductEvaluation lowerReturn = evaluation(product(
                "LOW_SAVING",
                ProductType.SAVING,
                10_000L,
                1_000_000L,
                0.03
        ));

        PortfolioCandidate candidate = builder.buildGreedyCandidate(
                "BALANCED",
                List.of(),
                List.of(lowerReturn, highReturn),
                profile(0L, 500_000L),
                0.5
        );

        assertThat(candidate.allocations())
                .filteredOn(allocation -> allocation.slot().allocationType() == AllocationType.MONTHLY_SAVING)
                .extracting(allocation -> builder.monthlyAmount(allocation.slot()))
                .containsExactly(200_000L, 300_000L);
    }

    @Test
    void skipsProductWhoseRequirementsMakeTheActualPortfolioInfeasible() {
        SavingsProduct blockedProduct = product(
                "BLOCKED",
                ProductType.DEPOSIT,
                100_000L,
                1_000_000L,
                0.08
        );
        ProductEvaluation blocked = new ProductEvaluation(
                blockedProduct,
                0.08,
                0.08,
                0.08,
                0.08,
                0.08,
                0.08,
                0.0,
                true,
                List.of(),
                List.of(),
                new ResourceDemand(600_000L, 0, Map.of(), 0L)
        );
        ProductEvaluation feasible = evaluation(product(
                "FEASIBLE",
                ProductType.DEPOSIT,
                100_000L,
                1_000_000L,
                0.04
        ));

        PortfolioCandidate candidate = builder.buildGreedyCandidate(
                "BALANCED",
                List.of(),
                List.of(blocked, feasible),
                profile(1_000_000L, 0L),
                0.5
        );

        assertThat(candidate.allocations())
                .extracting(allocation -> allocation.product().product().productId())
                .containsExactly("FEASIBLE");
        assertThat(candidate.lumpSumPrincipal()).isEqualTo(1_000_000L);
    }

    @Test
    void reservesPartOfInitialCashToFundAHighReturnMonthlySaving() {
        ProductEvaluation deposit = evaluation(product(
                "DEPOSIT",
                ProductType.DEPOSIT,
                100_000L,
                1_200_000L,
                0.04
        ));
        ProductEvaluation saving = evaluation(product(
                "SAVING",
                ProductType.SAVING,
                10_000L,
                150_000L,
                0.10
        ));

        PortfolioCandidate candidate = builder.buildGreedyCandidate(
                "BALANCED",
                List.of(),
                List.of(deposit, saving),
                profile(1_200_000L, 100_000L),
                0.5
        );

        assertThat(candidate.allocations())
                .extracting(allocation -> allocation.product().product().productId())
                .containsExactly("SAVING", "DEPOSIT");
        assertThat(candidate.allocations())
                .filteredOn(allocation -> allocation.slot().allocationType() == AllocationType.MONTHLY_SAVING)
                .extracting(allocation -> builder.monthlyAmount(allocation.slot()))
                .containsExactly(150_000L);
        assertThat(candidate.lumpSumPrincipal()).isEqualTo(600_000L);
        assertThat(candidate.principal()).isEqualTo(2_400_000L);

        var roadmap = new RoadmapEngine().create(
                List.of(deposit.product(), saving.product()),
                profile(1_200_000L, 100_000L),
                candidate.allocations().stream()
                        .map(allocation -> new SelectedAllocation(
                                allocation.slotIndex(),
                                allocation.product().product().productId(),
                                allocation.slot().amount(),
                                allocation.slot().startMonth()
                        ))
                        .toList()
        );
        assertThat(roadmap.summary().totalPrincipal()).isEqualTo(2_400_000L);
    }

    private ProductEvaluation evaluation(SavingsProduct product) {
        return new ProductEvaluation(
                product,
                product.baseRate(),
                product.baseRate(),
                product.baseRate(),
                product.baseRate(),
                product.baseRate(),
                product.baseRate(),
                0.0,
                true,
                List.of(),
                List.of(),
                new ResourceDemand(0L, 0, Map.of(), 0L)
        );
    }

    private SavingsProduct product(
            String id,
            ProductType type,
            long minimumAmount,
            long maximumAmount,
            double rate
    ) {
        return new SavingsProduct(
                id,
                id,
                id + "_BANK",
                type,
                12,
                minimumAmount,
                maximumAmount,
                rate,
                rate,
                List.of()
        );
    }

    private UserProfile profile(long lumpSum, long monthlySaving) {
        return new UserProfile(
                EmploymentType.FULL_TIME,
                true,
                lumpSum,
                0L,
                monthlySaving,
                12,
                List.of(0L, 0L, 0L, 0L, 0L, 0L),
                500_000L,
                List.of()
        );
    }
}
