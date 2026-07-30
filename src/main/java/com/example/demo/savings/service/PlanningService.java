package com.example.demo.savings.service;

import com.example.demo.savings.api.OptionsRequest;
import com.example.demo.savings.api.OptionsResponse;
import com.example.demo.savings.api.RoadmapRequest;
import com.example.demo.savings.api.RoadmapResponse;
import com.example.demo.savings.domain.AllocationSlot;
import com.example.demo.savings.domain.AllocationType;
import com.example.demo.savings.domain.SelectedAllocation;
import org.springframework.stereotype.Service;

@Service
public class PlanningService {

    private final ProductCatalog productCatalog;
    private final PortfolioOptimizer optimizer = new PortfolioOptimizer();
    private final RoadmapEngine roadmapEngine = new RoadmapEngine();

    public PlanningService(ProductCatalog productCatalog) {
        this.productCatalog = productCatalog;
    }

    public OptionsResponse options(OptionsRequest request) {
        // ProfileMapper를 통해 DTO -> 도메인 변환
        var domainProfile = ProfileMapper.toDomain(request.profile());

        // 🎯 유저가 선택한 목표 기간 (6, 12, 24, 36 등)
        int targetMonths = domainProfile.targetMonths();

        long allocatable = Math.max(
                0L,
                domainProfile.lumpSum() - domainProfile.emergencyFund()
        );

        java.util.List<AllocationSlot> slots = new java.util.ArrayList<>();

        // 1. 목돈/예금 슬롯 (12개월 고정 -> targetMonths 반영)
        if (allocatable > 0L) {
            slots.add(new AllocationSlot(
                    AllocationType.LUMP_SUM,
                    allocatable,
                    targetMonths
            ));
        }

        // 2. 월 적금 슬롯 (12개월 고정 -> targetMonths 반영 및 총액 재계산)
        if (domainProfile.monthlySaving() > 0L) {
            slots.add(new AllocationSlot(
                    AllocationType.MONTHLY_SAVING,
                    domainProfile.monthlySaving() * (long) targetMonths,
                    targetMonths
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
                                allocation.amount()
                        ))
                        .toList()
        );
    }
}