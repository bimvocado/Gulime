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
            // 💡 [수정] 1차(50%)와 2차(50%)의 총 납입액 합계가 온전히 1,200만 원이 되도록 조정
            long totalBudget = monthlySaving * (long) targetMonths;
            long halfBudget = totalBudget / 2; // 예: 600만 원

            // 1차: 1개월차 즉시 가입 슬롯 (600만 원)
            stableSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, halfBudget, targetMonths, 0));

            // 2차: 3개월차 시차 가입 슬롯 (남은 600만 원, 가입 기간은 remainingMonths)
            int remainingMonths = Math.max(1, targetMonths - 2);
            long secondHalfBudget = totalBudget - halfBudget; // 정확히 남은 잔액 600만 원
            stableSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, secondHalfBudget, remainingMonths, 2));

            // 💡 3차: 한도 초과 등으로 남을 자금을 받아줄 100% 완충용 백업 슬롯
            stableSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, totalBudget, targetMonths, 0));
        }

        // =========================================================================
        // 👑 2. [최적형 BALANCED] : 몬테카를로 AI 기대 금리 E[r] 기반 최적 가성비
        // =========================================================================
        List<AllocationSlot> balancedSlots = new ArrayList<>();
        if (allocatable > 0L) {
            balancedSlots.add(new AllocationSlot(AllocationType.LUMP_SUM, allocatable, targetMonths, 0));
        }
        if (monthlySaving > 0L) {
            long totalMonthlySavingBudget = monthlySaving * (long) targetMonths;
            balancedSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, totalMonthlySavingBudget, targetMonths, 0));
            // 💡 한도 소진 시 잔여 예산을 2차 상품에 채우기 위한 백업 슬롯 추가
            balancedSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, totalMonthlySavingBudget, targetMonths, 0));
        }

        // =========================================================================
        // 🔥 3. [수익형 AGGRESSIVE] : 표면 최고 우대 금리(Max Rate) 몰빵
        // =========================================================================
        List<AllocationSlot> aggressiveSlots = new ArrayList<>();
        if (allocatable > 0L) {
            aggressiveSlots.add(new AllocationSlot(AllocationType.LUMP_SUM, allocatable, targetMonths, 0));
        }
        if (monthlySaving > 0L) {
            long totalMonthlySavingBudget = monthlySaving * (long) targetMonths;
            // 최고 금리 상품 1차 슬롯
            aggressiveSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, totalMonthlySavingBudget, targetMonths, 0));
            // 💡 한도 초과 시 나머지 돈을 담아줄 2차 백업 슬롯
            aggressiveSlots.add(new AllocationSlot(AllocationType.MONTHLY_SAVING, totalMonthlySavingBudget, targetMonths, 0));
        }

        return optimizer.optimize(
                productCatalog.findAll(),
                domainProfile,
                request.riskTolerance(),
                stableSlots,
                balancedSlots,
                aggressiveSlots
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