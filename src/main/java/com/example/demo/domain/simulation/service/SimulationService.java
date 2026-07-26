package com.example.demo.domain.simulation.service;

import com.example.demo.domain.product.entity.Product;
import com.example.demo.domain.product.entity.ProductCondition;
import com.example.demo.domain.product.repository.ProductRepository;
import com.example.demo.domain.simulation.dto.SimulationRequestDto;
import com.example.demo.domain.simulation.dto.SimulationResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulationService {

    private final ProductRepository productRepository;
    private static final int MONTE_CARLO_TRIALS = 10000; // D1 스펙: 10,000회 시뮬레이션

    public SimulationResponseDto simulateProduct(SimulationRequestDto request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품 ID: " + request.getProductId()));

        double meanCard = calculateMean(request.getRecent6mCardSpend());
        double stdDevCard = calculateStdDev(request.getRecent6mCardSpend(), meanCard);

        List<SimulationResponseDto.ConditionEvaluationDto> evalList = new ArrayList<>();
        double totalExpectedBonus = 0.0;

        for (ProductCondition cond : product.getConditions()) {
            double prob = 0.0;
            String reason = "";

            if ("CARD_SPEND".equals(cond.getType()) && cond.getThreshold() != null) {
                int periodMonths = cond.getPeriodMonths() != null ? cond.getPeriodMonths() : 12;
                int requiredMonths = cond.getRequiredMonths() != null ? cond.getRequiredMonths() : 6;

                // [D1 스펙] 정규분포 St = max(0, N(mean, stdDev)) 기반 T개월 중 m개월 이상 달성 확률 계산
                prob = calculateCardSpendProbability(
                        meanCard, stdDevCard, cond.getThreshold(), periodMonths, requiredMonths, MONTE_CARLO_TRIALS
                );

                reason = String.format("최근 6개월 평균 %.1f만원 기준, %d개월 중 %d개월 이상 월 %.0f만원 달성 확률 %.1f%%",
                        meanCard / 10000.0, periodMonths, requiredMonths, (double) cond.getThreshold() / 10000.0, prob * 100);

            } else if ("SALARY_TRANSFER".equals(cond.getType())) {
                boolean isAvailable = Boolean.TRUE.equals(request.getSalaryTransferAvailable());
                prob = isAvailable ? 1.0 : 0.0;
                reason = isAvailable ? "급여 이체 조건 충족 (100%)" : "급여 이체 불가능 (0%)";
            } else {
                prob = 0.8;
                reason = "기본 우대 조건 충족 추정 (80%)";
            }

            // [D2 스펙] 기대금리 = 기본금리 + Sum(달성확률 * 우대금리)
            double rateBonus = cond.getRateBonus() != null ? cond.getRateBonus().doubleValue() : 0.0;
            totalExpectedBonus += (prob * rateBonus);

            // [D1 스펙] 부트스트랩 기반 90% 불확실성 구간 (5백분위수 ~ 95백분위수)
            double[] confidenceInterval = calculateBootstrapCI(request.getRecent6mCardSpend(), cond);

            evalList.add(SimulationResponseDto.ConditionEvaluationDto.builder()
                    .sourceText(cond.getSourceText())
                    .achievementProbability(BigDecimal.valueOf(prob * 100).setScale(2, RoundingMode.HALF_UP))
                    .confidenceMin(BigDecimal.valueOf(confidenceInterval[0] * 100).setScale(2, RoundingMode.HALF_UP))
                    .confidenceMax(BigDecimal.valueOf(confidenceInterval[1] * 100).setScale(2, RoundingMode.HALF_UP))
                    .reason(reason)
                    .build());
        }

        // 최종 기대금리 E[r] 산출
        double baseRate = product.getBaseRate().doubleValue();
        double expectedRate = baseRate + totalExpectedBonus;

        return SimulationResponseDto.builder()
                .productId(product.getProductId())
                .baseRate(product.getBaseRate())
                .expectedRate(BigDecimal.valueOf(expectedRate).setScale(2, RoundingMode.HALF_UP))
                .conditionEvaluations(evalList)
                .build();
    }

    // [D1 스펙] 정규분포 St = max(0, N(mean, stdDev)) 몬테카를로 시뮬레이션
    private double calculateCardSpendProbability(double mean, double stdDev, long threshold, int periodMonths, int requiredMonths, int trials) {
        Random random = new Random();
        int successTrialCount = 0;

        for (int i = 0; i < trials; i++) {
            int passedMonths = 0;
            for (int m = 0; m < periodMonths; m++) {
                // 음수 0원 처리: St = max(0, N(mean, stdDev))
                double simulatedSpend = Math.max(0.0, mean + random.nextGaussian() * stdDev);
                if (simulatedSpend >= threshold) {
                    passedMonths++;
                }
            }
            if (passedMonths >= requiredMonths) {
                successTrialCount++;
            }
        }
        return (double) successTrialCount / trials;
    }

    // [D1 스펙] 6개월 원본 복원추출 1,000회 부트스트랩 -> 5% / 95% 백분위수 구간 추출
    private double[] calculateBootstrapCI(List<Long> originalSpend, ProductCondition cond) {
        if (originalSpend == null || originalSpend.isEmpty() || !"CARD_SPEND".equals(cond.getType())) {
            return new double[]{0.0, 1.0};
        }

        int bootstrapTrials = 1000;
        double[] sampledProbs = new double[bootstrapTrials];
        Random random = new Random();
        int n = originalSpend.size();

        for (int b = 0; b < bootstrapTrials; b++) {
            List<Long> resampled = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                resampled.add(originalSpend.get(random.nextInt(n))); // 복원추출
            }
            double mean = calculateMean(resampled);
            double stdDev = calculateStdDev(resampled, mean);
            sampledProbs[b] = calculateCardSpendProbability(mean, stdDev, cond.getThreshold(),
                    cond.getPeriodMonths() != null ? cond.getPeriodMonths() : 12,
                    cond.getRequiredMonths() != null ? cond.getRequiredMonths() : 6, 500);
        }

        Arrays.sort(sampledProbs);
        double p5 = sampledProbs[(int) (bootstrapTrials * 0.05)];  // 5백분위수
        double p95 = sampledProbs[(int) (bootstrapTrials * 0.95)]; // 95백분위수

        return new double[]{p5, p95};
    }

    private double calculateMean(List<Long> values) {
        if (values == null || values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(Long::doubleValue).average().orElse(0.0);
    }

    private double calculateStdDev(List<Long> values, double mean) {
        if (values == null || values.size() <= 1) return 0.0;
        double sumSq = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum();
        return Math.sqrt(sumSq / (values.size() - 1));
    }
}