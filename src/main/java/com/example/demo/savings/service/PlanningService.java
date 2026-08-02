package com.example.demo.savings.service;

import com.example.demo.savings.api.OptionsRequest;
import com.example.demo.savings.api.OptionsResponse;
import com.example.demo.savings.api.RoadmapRequest;
import com.example.demo.savings.api.RoadmapResponse;
import com.example.demo.savings.domain.AllocationSlot;
import com.example.demo.savings.domain.AllocationType;
import com.example.demo.savings.domain.SelectedAllocation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlanningService {

    private final ProductCatalog productCatalog;
    private final PortfolioOptimizer optimizer = new PortfolioOptimizer();
    private final RoadmapEngine roadmapEngine = new RoadmapEngine();

    public PlanningService(ProductCatalog productCatalog) {
        this.productCatalog = productCatalog;
    }

    public OptionsResponse options(OptionsRequest request) {
        var domainProfile = ProfileMapper.toDomain(request.profile());
        int targetMonths = domainProfile.targetMonths();

        long allocatable = Math.max(
                0L,
                domainProfile.lumpSum() - domainProfile.emergencyFund()
        );

        long monthlySaving = domainProfile.monthlySaving();

        if (allocatable <= 0L && monthlySaving <= 0L) {
            throw new IllegalArgumentException("배분할 목돈 또는 월 저축 여력이 없습니다.");
        }

        // =========================================================================
        // 🛡️ 1. [안정형 STABLE] : 기본 보장 금리(Base Rate) + 시차 분산 가입 (풍차형)
        // - 전략: 우대 조건 실패 리스크를 제거한 "기본 금리" 최상위 상품 배치 & 만기 분산
        // =========================================================================
        List<AllocationSlot> stableSlots = new ArrayList<>();
        if (allocatable > 0L) {
            stableSlots.add(new AllocationSlot(AllocationType.LUMP_SUM, allocatable, targetMonths, 0));
        }
        if (monthlySaving > 0L) {
            long halfMonthlyBudget = (monthlySaving / 2) * (long) targetMonths;
            // 1차: 1개월차 즉시 가입 (유동성 확보용 50%)
            stableSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, halfMonthlyBudget, targetMonths, 0));
            // 2차: 3개월차 순차 가입 (풍차돌리기 50%)
            int remainingMonths = Math.max(1, targetMonths - 2);
            long secondHalfBudget = (monthlySaving - (monthlySaving / 2)) * (long) remainingMonths;
            stableSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, secondHalfBudget, remainingMonths, 2));
        }

        // =========================================================================
        // 👑 2. [최적형 BALANCED] : 몬테카를로 AI 기대 금리 E[r] 기반 최적 가성비
        // - 전략: 달성 확률 P(Condition)을 반영해 "실제 통장에 찍힐 기대 수익" 1위 조합
        // =========================================================================
        List<AllocationSlot> balancedSlots = new ArrayList<>();
        if (allocatable > 0L) {
            balancedSlots.add(new AllocationSlot(AllocationType.LUMP_SUM, allocatable, targetMonths, 0));
        }
        if (monthlySaving > 0L) {
            long totalMonthlySavingBudget = monthlySaving * (long) targetMonths;
            // E[r] 점수가 가장 높은 AI 추천 주력 슬롯
            balancedSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, totalMonthlySavingBudget, targetMonths, 0));
        }

        // =========================================================================
        // 🔥 3. [수익형 AGGRESSIVE] : 표면 최고 우대 금리(Max Rate) 몰빵
        // - 전략: 조건 완벽 달성(100% 성공) 전제, 한도 끝까지 100% 즉시 몰빵
        // =========================================================================
        List<AllocationSlot> aggressiveSlots = new ArrayList<>();
        if (allocatable > 0L) {
            aggressiveSlots.add(new AllocationSlot(AllocationType.LUMP_SUM, allocatable, targetMonths, 0));
        }
        if (monthlySaving > 0L) {
            long totalMonthlySavingBudget = monthlySaving * (long) targetMonths;
            // 최고 명시 금리 상품에 100% 한도 즉시 몰빵 슬롯
            aggressiveSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, totalMonthlySavingBudget, targetMonths, 0));
        }

        // 💡 몬테카를로 시뮬레이션 결과(E[r])와 플랜별 정렬 전략(Strategy)을 Optimizer에 함께 전달
        return optimizer.optimize(
                productCatalog.findAll(),
                domainProfile,
                request.riskTolerance(),
                stableSlots,    // 전략: BASE_RATE_ORIENTED (기본금리 위주)
                balancedSlots,  // 전략: MONTE_CARLO_EXPECTED_RATE (E[r] 기대금리 위주)
                aggressiveSlots // 전략: MAXIMUM_RATE_ORIENTED (최고 우대금리 위주)
        );
    }

    public RoadmapResponse roadmap(RoadmapRequest request) {
        return roadmapEngine.create(
                productCatalog.findAll(),
                ProfileMapper.toDomain(request.profile()),
                request.selectedAllocations().stream()
                        .map(allocation -> new SelectedAllocation(
                                allocation.slotIndex(),
                                allocation.productId(),
                                allocation.amount(),
                                allocation.startMonth()
                        ))
                        .toList()
        );
    }
}