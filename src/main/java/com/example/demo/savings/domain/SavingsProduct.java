package com.example.demo.savings.domain;

import java.util.List;

public record SavingsProduct(
        String productId,
        String productName,
        String bankName,
        ProductType productType,
        int termMonths,
        long minimumAmount,
        long maximumAmount,
        double baseRate,
        double maxRate,
        List<ProductCondition> conditions
) {
    public SavingsProduct {
        conditions = List.copyOf(conditions);
    }

    public SavingsProduct(
            String productId,
            String productName,
            String bankName,
            double baseRate,
            double maxRate,
            List<ProductCondition> conditions
    ) {
        this(
                productId,
                productName,
                bankName,
                ProductType.DEPOSIT,
                12,
                0L,
                Long.MAX_VALUE,
                baseRate,
                maxRate,
                conditions
        );
    }
}

