package com.example.demo;

import com.example.demo.savings.api.OptionsRequest;
import com.example.demo.savings.api.OptionsResponse;
import com.example.demo.savings.api.ProfileRequest;
import com.example.demo.savings.api.RoadmapRequest;
import com.example.demo.savings.api.RoadmapResponse;
import com.example.demo.savings.api.SelectedAllocationRequest;
import com.example.demo.savings.api.SimulateRequest;
import com.example.demo.savings.api.SimulateResponse;
import com.example.demo.savings.domain.ConditionType;
import com.example.demo.savings.domain.EmploymentType;
import com.example.demo.savings.service.PlanningService;
import com.example.demo.savings.service.ProductCatalog;
import com.example.demo.savings.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

        RoadmapResponse roadmap = planningService.roadmap(
                new RoadmapRequest(
                        profile,
                        balanced.allocations().stream()
                                .map(allocation ->
                                        new SelectedAllocationRequest(
                                                allocation.slotIndex(),
                                                allocation.productId(),
                                                allocation.amount()
                                        ))
                                .toList()
                )
        );
        assertThat(roadmap.initialAllocation()).isNotNull();
        assertThat(roadmap.milestones()).isNotEmpty();
        assertThat(roadmap.finalConfirmationRisks()).isNotNull();
        assertThat(roadmap.summary()).isNotNull();
        assertThat(objectMapper.writeValueAsString(roadmap))
                .contains(
                        "\"initialAllocation\"",
                        "\"milestones\"",
                        "\"finalConfirmationRisks\""
                );
    }
}
