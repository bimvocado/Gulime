package com.example.demo.domain.roadmap.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RoadmapRequestDto {
    private String selectedStyle;       // 사용자가 선택한 플랜 ("STABLE", "BALANCED", "AGGRESSIVE")
    private List<String> productIds;    // 선택된 상품 ID 목록
    private Long monthlySavingsCapacity; // 월 저축 여력
}