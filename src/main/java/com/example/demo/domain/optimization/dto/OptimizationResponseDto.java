package com.example.demo.domain.optimization.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class OptimizationResponseDto {
    private List<OptionDto> options;

    @Getter
    @Builder
    public static class OptionDto {
        private String style;               // "STABLE", "BALANCED", "AGGRESSIVE"
        private Long expectedFinalAmount;    // 12개월 뒤 예상 총액
        private Double risk;                 // 실현금리 표준편차 σ[r]
        private Long cardBudgetUsed;         // 카드 요구액 합계
        private List<String> products;      // 선택된 상품 ID 리스트
    }
}