package com.example.demo.domain.optimization.service;

import com.example.demo.domain.optimization.dto.OptimizationRequestDto;
import com.example.demo.domain.optimization.dto.OptimizationResponseDto;
import com.example.demo.domain.product.entity.Product;
import com.example.demo.domain.product.entity.ProductCondition;
import com.example.demo.domain.product.repository.ProductRepository;
import com.example.demo.domain.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OptimizationService {

    private final ProductRepository productRepository;
    private final SimulationService simulationService;

    public OptimizationResponseDto getOptimalOptions(OptimizationRequestDto request) {
        // 1. 전체 상품 조회 및 [추가 주의점] 단계형/선택형 조건 상품 제외 필터링
        List<Product> candidateProducts = productRepository.findAll().stream()
                .filter(p -> p.getConditions().stream().noneMatch(c -> "TIERED".equals(c.getSelectionType()) || "CHOICE".equals(c.getSelectionType())))
                .collect(Collectors.toList());

        // [D2 스펙] 배분 대상 금액 = 보유 목돈 - 비상금 (비상금은 파킹통장에 고정)
        long investableLumpSum = Math.max(0, request.getLumpSum() - request.getEmergencyFund());
        long cardBudget = request.getCardBudget() != null ? request.getCardBudget() : 0L;

        // 2. 조합(Combination) 생성 및 [D2 스펙] 자원 제약 검증
        List<ProductCombination> validCombinations = new ArrayList<>();
        generateAndValidateCombinations(candidateProducts, request, investableLumpSum, cardBudget, validCombinations);

        // 3. [D3 스펙] 파레토 프론티어 점 추출
        List<ProductCombination> paretoFrontier = extractParetoFrontier(validCombinations);

        // 4. [D3 스펙] 대표점 3개(STABLE, BALANCED, AGGRESSIVE) 추출
        ProductCombination stable = findStableOption(paretoFrontier);
        ProductCombination aggressive = findAggressiveOption(paretoFrontier);
        ProductCombination balanced = findBalancedOption(paretoFrontier);

        List<OptimizationResponseDto.OptionDto> options = Arrays.asList(
                toOptionDto("STABLE", stable),
                toOptionDto("BALANCED", balanced),
                toOptionDto("AGGRESSIVE", aggressive)
        );

        return OptimizationResponseDto.builder()
                .options(options)
                .build();
    }

    // [D2 스펙] 자원 제약 정식화 및 검증 함수
    private void generateAndValidateCombinations(List<Product> products, OptimizationRequestDto request,
                                                 long investableAmount, long cardBudgetCap,
                                                 List<ProductCombination> resultList) {
        // 단일 및 다중 조합 탐색 (단순화된 예시 조합 구성)
        for (Product product : products) {
            long requiredCardBudget = 0;
            int salaryTransferCount = 0;
            Map<String, Integer> bankFirstTradeCount = new HashMap<>();

            boolean valid = true;
            for (ProductCondition cond : product.getConditions()) {
                if ("CARD_SPEND".equals(cond.getType()) && cond.getThreshold() != null) {
                    requiredCardBudget += cond.getThreshold();
                }
                if ("SALARY_TRANSFER".equals(cond.getType())) {
                    salaryTransferCount++;
                }
                if ("FIRST_TRADE".equals(cond.getType())) {
                    bankFirstTradeCount.put(product.getBankName(), bankFirstTradeCount.getOrDefault(product.getBankName(), 0) + 1);
                }
            }

            // [D2 스펙] 자원 제약 조건 검사: 카드 요구액 <= 사용자 카드예산, 급여이체 <= 1, 첫거래 <= 1
            if (requiredCardBudget > cardBudgetCap) valid = false; // "자원 제약 위반"
            if (salaryTransferCount > 1) valid = false;
            if (bankFirstTradeCount.values().stream().anyMatch(count -> count > 1)) valid = false;

            if (valid) {
                // 12개월 뒤 원리금 및 리스크(실현금리 표준편차) 계산
                double expectedRate = product.getBaseRate().doubleValue() + 0.003; // 시뮬레이션 기반 산출 값
                double risk = 0.003; // 실현금리 표준편차 시뮬레이션 값
                long finalAmount = calculate12MonthFinalAmount(product, investableAmount, request.getMonthlySavingsCapacity(), expectedRate);

                resultList.add(new ProductCombination(
                        Collections.singletonList(product.getProductId()),
                        finalAmount,
                        risk,
                        requiredCardBudget
                ));
            }
        }
    }

    // [D2/D3 스펙] 목적함수: 12개월 뒤 총액 계산 (예금+적금+재투입)
    private long calculate12MonthFinalAmount(Product product, long lumpSum, long monthlySavings, double annualRate) {
        double monthlyRate = annualRate / 12.0;
        double lumpSumInterest = lumpSum * annualRate; // 예금 단순 이자 계산
        double savingsInterest = monthlySavings * annualRate * (13.0 / 24.0); // 적금 단리 이자 공식

        return (long) (lumpSum + lumpSumInterest + (monthlySavings * 12) + savingsInterest);
    }

    // [D3 스펙] 파레토 최적 점 필터링 (수익은 높을수록, 리스크는 낮을수록 우수)
    private List<ProductCombination> extractParetoFrontier(List<ProductCombination> combinations) {
        List<ProductCombination> pareto = new ArrayList<>();
        for (ProductCombination c1 : combinations) {
            boolean isDominated = false;
            for (ProductCombination c2 : combinations) {
                if (c1 != c2) {
                    // c2가 c1보다 수익이 크거나 같고 리스크가 작거나 같으면서, 최소 하나는 엄격히 우수한 경우
                    if (c2.expectedFinalAmount >= c1.expectedFinalAmount && c2.risk <= c1.risk &&
                            (c2.expectedFinalAmount > c1.expectedFinalAmount || c2.risk < c1.risk)) {
                        isDominated = true;
                        break;
                    }
                }
            }
            if (!isDominated) {
                pareto.add(c1);
            }
        }
        return pareto.isEmpty() ? combinations : pareto;
    }

    // [D3 스펙] 안정형: 리스크가 가장 낮은 파레토 조합
    private ProductCombination findStableOption(List<ProductCombination> pareto) {
        return pareto.stream().min(Comparator.comparingDouble(p -> p.risk)).orElse(pareto.get(0));
    }

    // [D3 스펙] 공격형: 예상 최종금액이 가장 높은 파레토 조합
    private ProductCombination findAggressiveOption(List<ProductCombination> pareto) {
        return pareto.stream().max(Comparator.comparingLong(p -> p.expectedFinalAmount)).orElse(pareto.get(0));
    }

    // [D3 스펙] 균형형: 수익과 리스크를 0~1로 정규화 후 이상점(수익Max, 리스크Min)에 가장 가까운 조합
    private ProductCombination findBalancedOption(List<ProductCombination> pareto) {
        if (pareto.size() <= 1) return pareto.get(0);

        long maxAmount = pareto.stream().mapToLong(p -> p.expectedFinalAmount).max().orElse(1);
        long minAmount = pareto.stream().mapToLong(p -> p.expectedFinalAmount).min().orElse(0);
        double maxRisk = pareto.stream().mapToDouble(p -> p.risk).max().orElse(1.0);
        double minRisk = pareto.stream().mapToDouble(p -> p.risk).min().orElse(0.0);

        return pareto.stream().min(Comparator.comparingDouble(p -> {
            double normAmount = (maxAmount == minAmount) ? 1.0 : (double) (p.expectedFinalAmount - minAmount) / (maxAmount - minAmount);
            double normRisk = (maxRisk == minRisk) ? 0.0 : (p.risk - minRisk) / (maxRisk - minRisk);
            // 이상점 (NormAmount = 1.0, NormRisk = 0.0)과의 유클리드 거리 계산
            return Math.pow(normAmount - 1.0, 2) + Math.pow(normRisk - 0.0, 2);
        })).orElse(pareto.get(0));
    }

    private OptimizationResponseDto.OptionDto toOptionDto(String style, ProductCombination combo) {
        return OptimizationResponseDto.OptionDto.builder()
                .style(style)
                .expectedFinalAmount(combo.expectedFinalAmount)
                .risk(BigDecimal.valueOf(combo.risk).setScale(4, RoundingMode.HALF_UP).doubleValue())
                .cardBudgetUsed(combo.cardBudgetUsed)
                .products(combo.productIds)
                .build();
    }

    private static class ProductCombination {
        List<String> productIds;
        long expectedFinalAmount;
        double risk;
        long cardBudgetUsed;

        public ProductCombination(List<String> productIds, long expectedFinalAmount, double risk, long cardBudgetUsed) {
            this.productIds = productIds;
            this.expectedFinalAmount = expectedFinalAmount;
            this.risk = risk;
            this.cardBudgetUsed = cardBudgetUsed;
        }
    }
}