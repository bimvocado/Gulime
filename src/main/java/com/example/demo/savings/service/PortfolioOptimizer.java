package com.example.demo.savings.service;

import com.example.demo.savings.api.OptionsResponse;
import com.example.demo.savings.domain.AllocationSlot;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.UserProfile;

import java.util.List;

public final class PortfolioOptimizer {

    private final ProductEvaluator evaluator = new ProductEvaluator();
    private final PortfolioCandidateBuilder candidateBuilder = new PortfolioCandidateBuilder();
    private final PortfolioResponseMapper responseMapper = new PortfolioResponseMapper();

    /**
     * 플랜별(안정/최적/수익)로 차별화된 슬롯 구성과 몬테카를로 평가 결과를 바탕으로 최적 포트폴리오 산출
     */
    public OptionsResponse optimize(
            List<SavingsProduct> products,
            UserProfile profile,
            double riskTolerance,
            List<AllocationSlot> stableSlots,
            List<AllocationSlot> balancedSlots,
            List<AllocationSlot> aggressiveSlots
    ) {
        // 1. 몬테카를로 및 우대조건 확률 기반 상품 평가
        List<ProductEvaluation> evaluations = products.stream()
                .map(product -> evaluator.evaluate(product, profile))
                .toList();

        // 2. 각 플랜의 전략에 맞게 설계된 후보 생성 (1,200만 원 완충된 상태로 산출됨)
        List<PortfolioCandidate> candidates = List.of(
                candidateBuilder.buildGreedyCandidate("STABLE", stableSlots, evaluations, profile, riskTolerance),
                candidateBuilder.buildGreedyCandidate("BALANCED", balancedSlots, evaluations, profile, riskTolerance),
                candidateBuilder.buildGreedyCandidate("AGGRESSIVE", aggressiveSlots, evaluations, profile, riskTolerance)
        );

        long allocatable = Math.max(0L, profile.lumpSum() - profile.emergencyFund());
        // 3. 차별화된 수령액과 이자가 반영된 최종 응답 객체 생성
        return responseMapper.buildOptionsResponse(products, profile, candidates, evaluations, allocatable);
    }
}
