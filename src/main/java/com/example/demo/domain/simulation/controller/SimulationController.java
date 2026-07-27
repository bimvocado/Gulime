package com.example.demo.domain.simulation.controller;

import com.example.demo.domain.simulation.dto.SimulationRequestDto;
import com.example.demo.domain.simulation.dto.SimulationResponseDto;
import com.example.demo.domain.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulate")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    // POST /api/v1/simulate - 몬테카를로 기대금리 E[r] 및 리스크 σ[r] 진단
    @PostMapping
    public ResponseEntity<SimulationResponseDto> simulateProduct(@RequestBody SimulationRequestDto request) {
        SimulationResponseDto response = simulationService.simulateProduct(request);
        return ResponseEntity.ok(response);
    }
}