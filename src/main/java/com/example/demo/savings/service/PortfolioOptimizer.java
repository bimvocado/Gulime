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

    public OptionsResponse optimize(
            List<SavingsProduct> products,
            UserProfile profile,
            double riskTolerance,
            List<AllocationSlot> slots
    ) {
        List<ProductEvaluation> evaluations = products.stream()
                .map(product -> evaluator.evaluate(product, profile))
                .toList();

        // Builder 내부에서 동적 할당 및 스케쥴링을 자동으로 완결 처리하므로 그대로 호출
        List<PortfolioCandidate> candidates = List.of(
                candidateBuilder.buildGreedyCandidate("STABLE", slots, evaluations, profile, riskTolerance),
                candidateBuilder.buildGreedyCandidate("BALANCED", slots, evaluations, profile, riskTolerance),
                candidateBuilder.buildGreedyCandidate("AGGRESSIVE", slots, evaluations, profile, riskTolerance)
        );

        long allocatable = Math.max(0L, profile.lumpSum() - profile.emergencyFund());

        return responseMapper.buildOptionsResponse(products, profile, candidates, evaluations, allocatable);
    }
}