package com.example.demo.domain.product.dto;

import com.example.demo.domain.product.entity.Product;
import com.example.demo.domain.product.entity.ProductCondition;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class ProductResponseDto {
    private String productId;
    private String bankName;
    private String productName;
    private String productType;
    private BigDecimal baseRate;
    private BigDecimal maxRate;
    private Long maxLimit;
    private Integer periodMonths;
    private Boolean isVerified;
    private List<ConditionDto> conditions;

    @Getter
    @Builder
    public static class ConditionDto {
        private Long conditionId;
        private String type;
        private String resource;
        private Long threshold;
        private BigDecimal rateBonus;
        private String payoutType;
        private String sourceText;

        public static ConditionDto from(ProductCondition condition) {
            return ConditionDto.builder()
                    .conditionId(condition.getConditionId())
                    .type(condition.getType())
                    .resource(condition.getResource())
                    .threshold(condition.getThreshold())
                    .rateBonus(condition.getRateBonus())
                    .payoutType(condition.getPayoutType())
                    .sourceText(condition.getSourceText())
                    .build();
        }
    }

    public static ProductResponseDto from(Product product) {
        return ProductResponseDto.builder()
                .productId(product.getProductId())
                .bankName(product.getBankName())
                .productName(product.getProductName())
                .productType(product.getProductType())
                .baseRate(product.getBaseRate())
                .maxRate(product.getMaxRate())
                .maxLimit(product.getMaxLimit())
                .periodMonths(product.getPeriodMonths())
                .isVerified(product.getIsVerified())
                .conditions(product.getConditions().stream()
                        .map(ConditionDto::from)
                        .toList())
                .build();
    }
}