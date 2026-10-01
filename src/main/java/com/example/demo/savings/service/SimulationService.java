package com.example.demo.savings.service;

import com.example.demo.savings.api.SimulateRequest;
import com.example.demo.savings.api.SimulateResponse;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.UserProfile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SimulationService {

    private final ProductCatalog productCatalog;
    private final SimulationEngine simulationEngine = new SimulationEngine();

    public SimulationService(ProductCatalog productCatalog) {
        this.productCatalog = productCatalog;
    }

    public SimulateResponse simulate(SimulateRequest request) {
        List<SavingsProduct> products = request.productIds() == null
                || request.productIds().isEmpty()
                ? productCatalog.findAll()
                : request.productIds().stream()
                .map(productId -> productCatalog.findById(productId)
                        .orElseThrow(() -> new ProductNotFoundException(productId)))
                .toList();

        return simulationEngine.simulate(
                products,
                ProfileMapper.toDomain(request.profile())
        );
    }
}

