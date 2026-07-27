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
        long allocatable = Math.max(
                0L,
                request.profile().lumpSum() - request.profile().emergencyFund()
        );
        java.util.List<AllocationSlot> slots = new java.util.ArrayList<>();
        if (allocatable > 0L) {
            slots.add(new AllocationSlot(
                    AllocationType.LUMP_SUM,
                    allocatable,
                    12
            ));
        }
        if (request.profile().monthlySaving() > 0L) {
            slots.add(new AllocationSlot(
                    AllocationType.MONTHLY_SAVING,
                    request.profile().monthlySaving() * 12L,
                    12
            ));
        }
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("배분할 목돈 또는 월 저축 여력이 없습니다.");
        }

        return optimizer.optimize(
                productCatalog.findAll(),
                ProfileMapper.toDomain(request.profile()),
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
