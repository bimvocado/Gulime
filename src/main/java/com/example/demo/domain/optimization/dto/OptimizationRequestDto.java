package com.example.demo.domain.optimization.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OptimizationRequestDto {
    private Long lumpSum;                  // 보유 목돈
    private Long emergencyFund;            // 비상금 (파킹통장 고정)
    private Long monthlySavingsCapacity;   // 월 저축 여력
    private Long cardBudget;               // 사용자 지정 카드 예산 상한
}