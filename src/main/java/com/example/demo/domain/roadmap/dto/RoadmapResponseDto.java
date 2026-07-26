package com.example.demo.domain.roadmap.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RoadmapResponseDto {
    private String style;
    private List<MonthlyScheduleDto> monthlySchedules;

    @Getter
    @Builder
    public static class MonthlyScheduleDto {
        private Integer month;              // 1~12개월 차
        private String title;              // 해당 월의 주 목표
        private List<String> actionItems;  // 사용자가 실행해야 할 행동 항목 목록
    }
}