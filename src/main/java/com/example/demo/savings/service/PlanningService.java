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

    private static final int MAX_LADDER_SLOTS = 3;
    private static final long MIN_LUMP_SUM_SLOT_AMOUNT = 1_000_000L;

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

        // 목돈과 월 저축액을 최대 3개로 나누고 가입월을 한 달씩 엇갈리게 합니다.
        if (allocatable > 0L) {
            addLadderSlots(
                    slots,
                    AllocationType.LUMP_SUM,
                    allocatable,
                    targetMonths,
                    lumpSumSlotCount(allocatable)
            );
        }

        if (domainProfile.monthlySaving() > 0L) {
            addLadderSlots(
                    slots,
                    AllocationType.MONTHLY_SAVING,
                    domainProfile.monthlySaving() * (long) targetMonths,
                    targetMonths,
                    Math.min(MAX_LADDER_SLOTS, targetMonths)
            );
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

    private int lumpSumSlotCount(long allocatable) {
        return (int) Math.max(1L, Math.min(
                MAX_LADDER_SLOTS,
                allocatable / MIN_LUMP_SUM_SLOT_AMOUNT
        ));
    }

    private void addLadderSlots(
            java.util.List<AllocationSlot> slots,
            AllocationType type,
            long totalAmount,
            int termMonths,
            int slotCount
    ) {
        long baseAmount = totalAmount / slotCount;
        long remainder = totalAmount % slotCount;
        for (int index = 0; index < slotCount; index++) {
            slots.add(new AllocationSlot(
                    type,
                    baseAmount + (index < remainder ? 1L : 0L),
                    termMonths,
                    index
            ));
        }
    }
}
