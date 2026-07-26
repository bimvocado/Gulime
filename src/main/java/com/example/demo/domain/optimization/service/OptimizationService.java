package com.example.demo.domain.optimization.service;

import com.example.demo.domain.optimization.dto.OptimizationRequestDto;
import com.example.demo.domain.optimization.dto.OptimizationResponseDto;
import com.example.demo.domain.product.entity.Product;
import com.example.demo.domain.product.entity.ProductCondition;
import com.example.demo.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OptimizationService {

    private final ProductRepository productRepository;

    public OptimizationResponseDto getOptimalOptions(OptimizationRequestDto request) {
        // [주의점 반영] 단계형/선택형 조건 상품 제외 (단일 조건만 필터링)
        List<Product> candidateProducts = productRepository.findAll().stream()
                .filter(p -> p.getConditions().stream().noneMatch(c -> "TIERED".equals(c.getSelectionType()) || "CHOICE".equals(c.getSelectionType())))
                .collect(Collectors.toList());

        long investableLumpSum = Math.max(0, request.getLumpSum() - request.getEmergencyFund());
        long cardBudget = request.getCardBudget() != null ? request.getCardBudget() : 0L;

        // 자원 제약 검증 및 조합 생성
        List<ProductCombination> validCombinations = new ArrayList<>();
        generateAndValidateCombinations(candidateProducts, request, investableLumpSum, cardBudget, validCombinations);

        // 파레토 프론티어 필터링
        List<ProductCombination> paretoFrontier = extractParetoFrontier(validCombinations);

        // 대표 3안 (STABLE, BALANCED, AGGRESSIVE) 추출
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

    private void generateAndValidateCombinations(List<Product> products, OptimizationRequestDto request,
                                                 long investableAmount, long cardBudgetCap,
                                                 List<ProductCombination> resultList) {
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

            // D2 자원 제약 검증: 카드 요구액 <= 사용자 예산, 급여이체 <= 1, 첫거래 <= 1
            if (requiredCardBudget > cardBudgetCap) valid = false;
            if (salaryTransferCount > 1) valid = false;
            if (bankFirstTradeCount.values().stream().anyMatch(count -> count > 1)) valid = false;

            if (valid) {
                double expectedRate = product.getBaseRate().doubleValue() + 0.003;
                double risk = 0.003;
                long finalAmount = calculate12MonthFinalAmount(investableAmount, request.getMonthlySavingsCapacity(), expectedRate);

                resultList.add(new ProductCombination(
                        Collections.singletonList(product.getProductId()),
                        finalAmount,
                        risk,
                        requiredCardBudget
                ));
            }
        }
    }

    private long calculate12MonthFinalAmount(long lumpSum, long monthlySavings, double annualRate) {
        double lumpSumInterest = lumpSum * annualRate;
        double savingsInterest = monthlySavings * annualRate * (13.0 / 24.0); // 적금 단리 이자 공식
        return (long) (lumpSum + lumpSumInterest + (monthlySavings * 12) + savingsInterest);
    }

    private List<ProductCombination> extractParetoFrontier(List<ProductCombination> combinations) {
        List<ProductCombination> pareto = new ArrayList<>();
        for (ProductCombination c1 : combinations) {
            boolean isDominated = false;
            for (ProductCombination c2 : combinations) {
                if (c1 != c2) {
                    if (c2.expectedFinalAmount >= c1.expectedFinalAmount && c2.risk <= c1.risk &&
                            (c2.expectedFinalAmount > c1.expectedFinalAmount || c2.risk < c1.risk)) {
                        isDominated = true;
                        break;
                    }
                }
            }
            if (!isDominated) pareto.add(c1);
        }
        return pareto.isEmpty() ? combinations : pareto;
    }

    private ProductCombination findStableOption(List<ProductCombination> pareto) {
        return pareto.stream().min(Comparator.comparingDouble(p -> p.risk)).orElse(pareto.get(0));
    }

    private ProductCombination findAggressiveOption(List<ProductCombination> pareto) {
        return pareto.stream().max(Comparator.comparingLong(p -> p.expectedFinalAmount)).orElse(pareto.get(0));
    }

    private ProductCombination findBalancedOption(List<ProductCombination> pareto) {
        if (pareto.size() <= 1) return pareto.get(0);

        long maxAmount = pareto.stream().mapToLong(p -> p.expectedFinalAmount).max().orElse(1);
        long minAmount = pareto.stream().mapToLong(p -> p.expectedFinalAmount).min().orElse(0);
        double maxRisk = pareto.stream().mapToDouble(p -> p.risk).max().orElse(1.0);
        double minRisk = pareto.stream().mapToDouble(p -> p.risk).min().orElse(0.0);

        return pareto.stream().min(Comparator.comparingDouble(p -> {
            double normAmount = (maxAmount == minAmount) ? 1.0 : (double) (p.expectedFinalAmount - minAmount) / (maxAmount - minAmount);
            double normRisk = (maxRisk == minRisk) ? 0.0 : (p.risk - minRisk) / (maxRisk - minRisk);
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