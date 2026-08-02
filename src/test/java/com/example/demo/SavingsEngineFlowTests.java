package com.example.demo;

import com.example.demo.savings.api.OptionsRequest;
import com.example.demo.savings.api.OptionsResponse;
import com.example.demo.savings.api.ProfileRequest;
import com.example.demo.savings.api.RoadmapRequest;
import com.example.demo.savings.api.RoadmapResponse;
import com.example.demo.savings.api.SelectedAllocationRequest;
import com.example.demo.savings.api.SimulateRequest;
import com.example.demo.savings.api.SimulateResponse;
import com.example.demo.savings.catalog.JsonValidationProductCatalog;
import com.example.demo.savings.domain.ConditionType;
import com.example.demo.savings.domain.EmploymentType;
import com.example.demo.savings.domain.UserProfile;
import com.example.demo.savings.service.PlanningService;
import com.example.demo.savings.service.ProductCatalog;
import com.example.demo.savings.service.ProductEvaluator;
import com.example.demo.savings.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class SavingsEngineFlowTests {

    @Autowired
    private ProductCatalog productCatalog;

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private PlanningService planningService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validationProductCanFlowThroughAllEngines() {
        ProfileRequest profile = new ProfileRequest(
                EmploymentType.FREELANCER,
                false,
                5_000_000L,
                1_000_000L,
                500_000L,
                12,
                List.of(
                        220_000L,
                        310_000L,
                        180_000L,
                        270_000L,
                        350_000L,
                        170_000L
                ),
                300_000L,
                List.of("국민은행")
        );
        String productId = productCatalog.findAll().stream()
                .filter(product -> product.conditions().stream()
                        .anyMatch(condition ->
                                condition.type() == ConditionType.CARD_SPEND))
                .findFirst()
                .orElseThrow()
                .productId();

        SimulateResponse simulation = simulationService.simulate(
                new SimulateRequest(profile, List.of(productId))
        );
        assertThat(simulation.products()).hasSize(1);
        assertThat(simulation.products().get(0).productId())
                .isEqualTo(productId);
        assertThat(simulation.products().get(0)
                .expectedRateConfidenceInterval()).isNotNull();
        assertThat(simulation.products().get(0)
                .sensitivityAnalysis().robustness())
                .isIn("STABLE", "UNSTABLE");
        assertThat(simulation.products().get(0).conditionEvaluations())
                .allSatisfy(condition -> {
                    assertThat(condition.conditionId()).isNotBlank();
                    assertThat(condition.sourceText()).isNotBlank();
                    assertThat(condition.confidenceInterval()).isNotNull();
                    assertThat(condition.status()).isNotBlank();
                });
        assertThat(objectMapper.writeValueAsString(simulation))
                .contains(
                        "\"conditionId\"",
                        "\"sourceText\"",
                        "\"confidenceInterval\"",
                        "\"sensitivityProbabilityRange\"",
                        "\"robustness\""
                );

        OptionsResponse options = planningService.options(
                new OptionsRequest(
                        profile,
                        0.5
                )
        );
        assertThat(options.productAvailability()).isNotEmpty();
        assertThat(options.options())
                .extracting(option -> option.optionType())
                .containsExactly("STABLE", "BALANCED", "AGGRESSIVE");
        assertThat(objectMapper.writeValueAsString(options))
                .contains(
                        "\"options\"",
                        "\"productAvailability\"",
                        "\"allocationType\""
                );

        var balanced = options.options().stream()
                .filter(option -> option.optionType().equals("BALANCED"))
                .findFirst()
                .orElseThrow();
        var stable = options.options().stream()
                .filter(option -> option.optionType().equals("STABLE"))
                .findFirst()
                .orElseThrow();
        var aggressive = options.options().stream()
                .filter(option -> option.optionType().equals("AGGRESSIVE"))
                .findFirst()
                .orElseThrow();
        long allocatable = profile.lumpSum() - profile.emergencyFund();
        long totalBudget = allocatable
                + profile.monthlySaving() * profile.targetMonths();
        assertThat(List.of(stable, balanced, aggressive))
                .allSatisfy(option -> {
                    assertThat(option.allocations()).isNotEmpty();

                    long principal = option.allocations().stream()
                            .mapToLong(allocation -> allocation.amount())
                            .sum();
                    long lumpSumPrincipal = option.allocations().stream()
                            .filter(allocation -> allocation.allocationType().equals("LUMP_SUM"))
                            .mapToLong(allocation -> allocation.amount())
                            .sum();
                    assertThat(principal).isLessThanOrEqualTo(totalBudget);
                    assertThat(lumpSumPrincipal).isLessThanOrEqualTo(allocatable);
                    assertThat(option.expectedFinalAmount())
                            .isGreaterThanOrEqualTo(principal);

                    long cash = allocatable - lumpSumPrincipal;
                    for (int month = 0; month < option.completionMonth(); month++) {
                        int currentMonth = month;
                        cash += profile.monthlySaving();
                        long monthlyExpense = option.allocations().stream()
                                .filter(allocation -> allocation.allocationType().equals("MONTHLY_SAVING"))
                                .filter(allocation -> currentMonth >= allocation.startMonth())
                                .filter(allocation -> currentMonth < allocation.maturityMonth())
                                .mapToLong(allocation -> allocation.monthlyAmount())
                                .sum();
                        assertThat(monthlyExpense).isLessThanOrEqualTo(cash + 1000L);
                        cash -= Math.min(cash, monthlyExpense);
                    }
                });
        assertThat(options.options())
                .flatExtracting(option -> option.allocations())
                .allSatisfy(allocation -> assertThat(allocation.maturityMonth())
                        .isEqualTo(allocation.startMonth()
                                + allocation.termMonths()));
        RoadmapResponse roadmap = planningService.roadmap(
                new RoadmapRequest(
                        profile,
                        balanced.allocations().stream()
                                .map(allocation ->
                                        new SelectedAllocationRequest(
                                                allocation.slotIndex(),
                                                allocation.productId(),
                                                allocation.amount(),
                                                allocation.startMonth()
                                        ))
                                .toList()
                )
        );
        assertThat(roadmap.initialAllocation()).isNotNull();
        assertThat(roadmap.milestones()).isNotEmpty();
        assertThat(roadmap.milestones())
                .filteredOn(milestone -> milestone.eventType().equals("SUBSCRIPTION"))
                .extracting(milestone -> milestone.month())
                .containsExactlyInAnyOrderElementsOf(
                        balanced.allocations().stream()
                                .map(allocation -> allocation.startMonth())
                                .toList()
                );
        assertThat(roadmap.finalConfirmationRisks()).isNotNull();
        assertThat(roadmap.summary()).isNotNull();
        assertThat(objectMapper.writeValueAsString(roadmap))
                .contains(
                        "\"initialAllocation\"",
                        "\"milestones\"",
                        "\"finalConfirmationRisks\""
                );
    }

    @Test
    void advancedCatalogSplitsTermsAndGroupsTierChoiceAndBranchConditions() {
        ProductCatalog advancedCatalog = new JsonValidationProductCatalog(
                objectMapper,
                new DefaultResourceLoader(),
                "classpath:catalog-advanced.json"
        );

        assertThat(advancedCatalog.findAll())
                .extracting(product -> product.termMonths())
                .containsExactly(1, 3, 6, 12, 24, 36);
        assertThat(advancedCatalog.findAll())
                .extracting(product -> product.productId())
                .allMatch(productId -> productId.matches(
                        "PRODUCT_[0-9A-F]{16}_(1|3|6|12|24|36)M"
                ));

        var threeMonth = advancedCatalog.findAll().stream()
                .filter(product -> product.termMonths() == 3)
                .findFirst()
                .orElseThrow();
        assertThat(threeMonth.baseRate())
                .isCloseTo(0.013, within(0.0000001));
        assertThat(threeMonth.maxRate())
                .isCloseTo(0.023, within(0.0000001));

        var twentyFourMonth = advancedCatalog.findAll().stream()
                .filter(product -> product.termMonths() == 24)
                .findFirst()
                .orElseThrow();
        assertThat(twentyFourMonth.baseRate())
                .isCloseTo(0.024, within(0.0000001));
        assertThat(twentyFourMonth.maxRate())
                .isCloseTo(0.036, within(0.0000001));

        var twelveMonth = advancedCatalog.findAll().stream()
                .filter(product -> product.termMonths() == 12)
                .findFirst()
                .orElseThrow();
        assertThat(twelveMonth.conditions().stream()
                .filter(condition -> condition.tierGroup() != null))
                .hasSize(2)
                .extracting(condition -> condition.tierGroup())
                .containsOnly(twelveMonth.productId() + "_C0_TIER");
        assertThat(twelveMonth.conditions().stream()
                .filter(condition -> condition.selectionGroup() != null))
                .hasSize(3)
                .allSatisfy(condition -> {
                    assertThat(condition.maxSelect()).isEqualTo(2);
                    assertThat(condition.selectionGroup()).endsWith("_CHOICE");
                });
        assertThat(twelveMonth.conditions())
                .anySatisfy(condition -> {
                    assertThat(condition.hardRequirement()).isTrue();
                    assertThat(condition.conditionName())
                            .isEqualTo("모바일 가입 필수");
                });

        var evaluation = new ProductEvaluator().evaluate(
                twelveMonth,
                new UserProfile(
                        EmploymentType.FULL_TIME,
                        true,
                        6_000_000L,
                        0L,
                        0L,
                        12,
                        List.of(
                                0L,
                                0L,
                                0L,
                                0L,
                                0L,
                                0L
                        ),
                        0L,
                        List.of(),
                        twelveMonth.conditions().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        condition -> condition.conditionId(),
                                        ignored -> true
                                ))
                )
        );

        assertThat(evaluation.expectedRate())
                .isCloseTo(0.0322, within(0.0000001));
        assertThat(evaluation.conditions().stream()
                .filter(item -> item.condition().tierGroup() != null)
                .filter(item -> item.selected()))
                .singleElement()
                .satisfies(item ->
                        assertThat(item.condition().rateBonus())
                                .isCloseTo(0.002, within(0.0000001)));
        assertThat(evaluation.conditions().stream()
                .filter(item -> item.condition().selectionGroup() != null)
                .filter(item -> item.selected()))
                .hasSize(2);
        assertThat(evaluation.conditions().stream()
                .filter(item -> item.condition().branch() != null)
                .filter(item -> item.selected()))
                .singleElement()
                .satisfies(item ->
                        assertThat(item.condition().branch())
                                .isEqualTo("NEW_CUSTOMER"));
    }
}
