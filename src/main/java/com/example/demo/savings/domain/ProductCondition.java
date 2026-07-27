package com.example.demo.savings.domain;

public record ProductCondition(
        String conditionId,
        String conditionName,
        String sourceText,
        ConditionType type,
        Long threshold,
        ResourceType resource,
        Integer periodMonths,
        Integer requiredMonths,
        Double rateBonus,
        boolean hardRequirement,
        Payout payout,
        ParseStatus parseStatus,
        String tierGroup,
        String selectionGroup,
        SelectionRule selectionRule,
        Integer maxSelect,
        String exclusiveGroup,
        String branch
) {
    public ProductCondition(
            String conditionId,
            String conditionName,
            String sourceText,
            ConditionType type,
            Long threshold,
            ResourceType resource,
            Integer periodMonths,
            Integer requiredMonths,
            Double rateBonus,
            boolean hardRequirement,
            Payout payout,
            ParseStatus parseStatus
    ) {
        this(
                conditionId,
                conditionName,
                sourceText,
                type,
                threshold,
                resource,
                periodMonths,
                requiredMonths,
                rateBonus,
                hardRequirement,
                payout,
                parseStatus,
                null,
                null,
                SelectionRule.ALL,
                null,
                null,
                null
        );
    }
}
