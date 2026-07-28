package com.example.demo.savings.api;

import java.util.List;

public record SimulateResponse(
        CardBudgetResponse cardBudget,
        List<ProductSimulationResponse> products
) {
    public SimulateResponse {
        products = List.copyOf(products);
    }
}

