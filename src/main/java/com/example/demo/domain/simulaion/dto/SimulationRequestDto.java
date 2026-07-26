package com.example.demo.domain.simulation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class SimulationRequestDto {

    private String productId;                 // 진단 대상 상품 ID
    private String workType;                  // 근로 형태 (FREELANCER, REGULAR 등)
    private Boolean salaryTransferAvailable;  // 급여이체 가능 여부
    private Long lumpSum;                     // 보유 목돈
    private Long emergencyFund;               // 비상금
    private Long monthlySavingsCapacity;      // 월 저축 여력
    private List<Long> recent6mCardSpend;    // 최근 6개월 카드 사용 내역 (6개 원화 금액)
    private List<String> existingBanks;      // 기존 거래 은행 목록

    @Builder.Default
    private Integer targetPeriodMonths = 12; // 목표 기간 (기본값 12개월)
}