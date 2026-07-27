package com.example.demo.savings.api;

import com.example.demo.savings.service.PlanningService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanningController {

    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @PostMapping("/options")
    public ResponseEntity<OptionsResponse> options(
            @Valid @RequestBody OptionsRequest request
    ) {
        return ResponseEntity.ok(planningService.options(request));
    }

    @PostMapping("/roadmap")
    public ResponseEntity<RoadmapResponse> roadmap(
            @Valid @RequestBody RoadmapRequest request
    ) {
        return ResponseEntity.ok(planningService.roadmap(request));
    }
}

