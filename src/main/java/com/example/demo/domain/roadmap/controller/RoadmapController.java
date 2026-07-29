package com.example.demo.domain.roadmap.controller;

import com.example.demo.domain.roadmap.dto.RoadmapRequestDto;
import com.example.demo.domain.roadmap.dto.RoadmapResponseDto;
import com.example.demo.domain.roadmap.service.RoadmapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roadmap")
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapService roadmapService;

    @PostMapping
    public ResponseEntity<RoadmapResponseDto> generateRoadmap(@RequestBody RoadmapRequestDto request) {
        return ResponseEntity.ok(roadmapService.generateRoadmap(request));
    }
}