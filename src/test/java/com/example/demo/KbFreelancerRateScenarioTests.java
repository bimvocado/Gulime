package com.example.demo;

import com.example.demo.savings.domain.ConditionType;
import com.example.demo.savings.domain.EmploymentType;
import com.example.demo.savings.domain.ParseStatus;
import com.example.demo.savings.domain.Payout;
import com.example.demo.savings.domain.ProductCondition;
import com.example.demo.savings.domain.ResourceType;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.SelectionRule;
import com.example.demo.savings.domain.UserProfile;
import com.example.demo.savings.service.SimulationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KbFreelancerRateScenarioTests {

    private final SimulationEngine engine = new SimulationEngine();

    @Test
    void unansweredConditionsAreReturnedAsAtMostThreeConfirmationQuestions() {
        var result = engine.simulate(List.of(kbMyWaySavings()), profile(Map.of()));
        var product = result.products().get(0);

        assertThat(product.expectedRate()).isEqualTo(2.85);
        assertThat(product.expectedRateConfidenceInterval().min()).isEqualTo(2.55);
        assertThat(product.expectedRateConfidenceInterval().max()).isEqualTo(3.15);
        assertThat(product.confirmationQuestions()).hasSize(3);
        assertThat(product.conditionEvaluations())
                .anySatisfy(condition ->
                        assertThat(condition.status()).isEqualTo("NEEDS_CONFIRMATION"));
    }

    @Test
    void confirmedFreelancerProfileCannotReachAdvertisedMaximumRate() {
        Map<String, Boolean> answers = Map.of(
                "CARD_ACCOUNT", true,
                "AUTO_SAVING", true,
                "APARTMENT_FEE", false,
                "STAR_BANKING", true,
                "LOYALTY", true,
                "HOUSING_SUBSCRIPTION", false,
                "SPECIAL_DAY", true
        );

        var result = engine.simulate(List.of(kbMyWaySavings()), profile(answers));
        var product = result.products().get(0);

        assertThat(product.baseRate()).isEqualTo(2.55);
        assertThat(product.advertisedMaxRate()).isEqualTo(3.15);
        assertThat(product.expectedRate()).isEqualTo(3.05);
        assertThat(product.profileAchievableMaxRate()).isEqualTo(3.05);
        assertThat(product.confirmationQuestions()).isEmpty();
    }

    private UserProfile profile(Map<String, Boolean> answers) {
        return new UserProfile(
                EmploymentType.FREELANCER,
                false,
                5_000_000L,
                1_000_000L,
                500_000L,
                12,
                List.of(220_000L, 310_000L, 180_000L, 270_000L, 350_000L, 170_000L),
                300_000L,
                List.of("국민은행"),
                answers
        );
    }

    private SavingsProduct kbMyWaySavings() {
        return new SavingsProduct(
                "0010927_010200100070_12M",
                "KB내맘대로적금",
                "국민은행",
                0.0255,
                0.0315,
                List.of(
                        condition("SALARY", "급여이체", ConditionType.SALARY_TRANSFER),
                        condition("CARD_ACCOUNT", "카드결제계좌", ConditionType.CARD_PAYMENT_ACCOUNT),
                        condition("AUTO_SAVING", "자동이체 저축", ConditionType.TRANSFER_COUNT),
                        condition("APARTMENT_FEE", "아파트관리비 이체", ConditionType.TRANSFER_COUNT),
                        condition("STAR_BANKING", "KB스타뱅킹 이체", ConditionType.CHANNEL_USE),
                        condition("LOYALTY", "장기거래", ConditionType.OTHER),
                        condition("FIRST_TRADE", "첫 거래", ConditionType.FIRST_TRADE),
                        condition("HOUSING_SUBSCRIPTION", "주택청약종합저축", ConditionType.PRODUCT_HOLDING),
                        condition("SPECIAL_DAY", "소중한 날", ConditionType.OTHER)
                )
        );
    }

    private ProductCondition condition(
            String id,
            String name,
            ConditionType type
    ) {
        ResourceType resource = switch (type) {
            case SALARY_TRANSFER -> ResourceType.SALARY_TRANSFER;
            case FIRST_TRADE -> ResourceType.FIRST_TRADE;
            default -> ResourceType.NONE;
        };
        return new ProductCondition(
                id,
                name,
                name,
                type,
                null,
                resource,
                null,
                null,
                0.001,
                false,
                Payout.AT_MATURITY,
                ParseStatus.COMPLETE,
                null,
                "KB_MY_WAY_SELECTION",
                SelectionRule.MAX_SELECT,
                6,
                null,
                null
        );
    }
}
