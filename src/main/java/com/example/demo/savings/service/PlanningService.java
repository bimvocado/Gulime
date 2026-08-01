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

        List<AllocationSlot> slots = new ArrayList<>();

        // 1. 목돈 슬롯 (예금)
        if (allocatable > 0L) {
            slots.add(new AllocationSlot(
                    AllocationType.LUMP_SUM,
                    allocatable,
                    targetMonths,
                    0
            ));
        }

        // 2. 월 적금 슬롯
        // 💡 [핵심 수정] 무작정 슬롯을 3개씩 넣지 말고, 적금 슬롯은 1~2개로 안전하게 할당
        if (domainProfile.monthlySaving() > 0L) {
            long totalMonthlySavingBudget = domainProfile.monthlySaving() * (long) targetMonths;

            // Primary 적금 슬롯 (1순위 고금리 상품용)
            slots.add(new AllocationSlot(
                    AllocationType.MONTHLY_SAVING,
                    totalMonthlySavingBudget,
                    targetMonths,
                    0
            ));

            // Secondary 적금 슬롯 (여유 자금 분산용 2순위 - 1개만 추가)
            slots.add(new AllocationSlot(
                    AllocationType.MONTHLY_SAVING,
                    totalMonthlySavingBudget,
                    targetMonths,
                    1
            ));
        }

        if (slots.isEmpty()) {
            throw new IllegalArgumentException("배분할 목돈 또는 월 저축 여력이 없습니다.");
        }

        return optimizer.optimize(
                productCatalog.findAll(),
                domainProfile,
                request.riskTolerance(),
                slots
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