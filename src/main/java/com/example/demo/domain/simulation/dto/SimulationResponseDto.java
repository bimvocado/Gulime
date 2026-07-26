package com.example.demo.domain.simulation.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SimulationResponseDto {

    private String productId;
    private BigDecimal baseRate;             // 기본 금리 (%)
    private BigDecimal expectedRate;         // 기대금리 E[r] (%)
    private BigDecimal rateRisk;             // 조건 실패 리스크 σ[r] (%)
    private BigDecimal achievementProb;      // 전체 조건 연계 달성 확률 (%)
    private BigDecimal confidenceMin;        // 95% 신뢰구간 Min (%)
    private BigDecimal confidenceMax;        // 95% 신뢰구간 Max (%)
    private List<ConditionEvaluationDto> conditionEvaluations;

    @Getter
    @Builder
    public static class ConditionEvaluationDto {
        private String sourceText;
        private BigDecimal achievementProbability; // 12개월 연속 달성 확률 (%)
        private BigDecimal confidenceMin;
        private BigDecimal confidenceMax;
        private String reason;                      // XAI 근거 텍스트 ("왜 XX%인가")
    }
}