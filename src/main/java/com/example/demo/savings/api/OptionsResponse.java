package com.example.demo.savings.api;

import java.util.List;

public record OptionsResponse(
        ResourceBudgetResponse resourceBudget,
        List<PortfolioResponse> options,
        List<AvailableProductResponse> productAvailability
) {
    public OptionsResponse {
        options = List.copyOf(options);
        productAvailability = List.copyOf(productAvailability);
    }
}
