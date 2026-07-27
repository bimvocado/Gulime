package com.example.demo.domain.optimization.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OptimizationRequestDto {
    private Long lumpSum;                  // 보유 목돈
    private Long emergencyFund;            // 비상금 (파킹통장에 고정 배분)
    private Long monthlySavingsCapacity;   // 월 저축 여력
    private Long cardBudget;               // 사용자 카드 예산 상한
}