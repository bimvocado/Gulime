package com.example.demo.savings.service;

import com.example.demo.savings.domain.AllocationSlot;

record PortfolioAllocation(
        int slotIndex,
        AllocationSlot slot,
        ProductEvaluation product
) {
}

