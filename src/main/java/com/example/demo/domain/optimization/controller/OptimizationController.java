package com.example.demo.domain.optimization.controller;

import com.example.demo.domain.optimization.dto.OptimizationRequestDto;
import com.example.demo.domain.optimization.dto.OptimizationResponseDto;
import com.example.demo.domain.optimization.service.OptimizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/options")
@RequiredArgsConstructor
public class OptimizationController {

    private final OptimizationService optimizationService;

    @PostMapping
    public ResponseEntity<OptimizationResponseDto> getOptimalOptions(@RequestBody OptimizationRequestDto request) {
        OptimizationResponseDto response = optimizationService.getOptimalOptions(request);
        return ResponseEntity.ok(response);
    }
}