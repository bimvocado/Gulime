package com.example.demo.domain.roadmap.service;

import com.example.demo.domain.product.entity.Product;
import com.example.demo.domain.product.entity.ProductCondition;
import com.example.demo.domain.product.repository.ProductRepository;
import com.example.demo.domain.roadmap.dto.RoadmapRequestDto;
import com.example.demo.domain.roadmap.dto.RoadmapResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoadmapService {

    private final ProductRepository productRepository;

    public RoadmapResponseDto generateRoadmap(RoadmapRequestDto request) {
        List<Product> products = productRepository.findAllById(request.getProductIds());
        List<RoadmapResponseDto.MonthlyScheduleDto> schedules = new ArrayList<>();

        // 1개월 차: 개설 및 초기 설정
        List<String> month1Actions = new ArrayList<>();
        for (Product p : products) {
            month1Actions.add(p.getProductName() + " 상품 개설");
            for (ProductCondition cond : p.getConditions()) {
                if ("SALARY_TRANSFER".equals(cond.getType())) {
                    month1Actions.add(p.getBankName() + " 계좌로 급여 이체 지정");
                }
                if ("CARD_SPEND".equals(cond.getType())) {
                    month1Actions.add(p.getBankName() + " 전용 카드발급 및 자동이체 연결");
                }
            }
        }
        schedules.add(RoadmapResponseDto.MonthlyScheduleDto.builder()
                .month(1)
                .title("상품 개설 및 자동이체 설정")
                .actionItems(month1Actions)
                .build());

        // 2~11개월 차: 매월 실적 유지 관리
        for (int m = 2; m <= 11; m++) {
            List<String> monthlyActions = new ArrayList<>();
            monthlyActions.add(String.format("월 적금액 %d원 자동이체 납입 확인", request.getMonthlySavingsCapacity()));

            for (Product p : products) {
                for (ProductCondition cond : p.getConditions()) {
                    if ("CARD_SPEND".equals(cond.getType()) && cond.getThreshold() != null) {
                        monthlyActions.add(String.format("%s 카드 최소 실적(월 %d원) 달성 체크",
                                p.getBankName(), cond.getThreshold()));
                    }
                }
            }

            schedules.add(RoadmapResponseDto.MonthlyScheduleDto.builder()
                    .month(m)
                    .title(m + "개월 차 조건 유지 및 저축")
                    .actionItems(monthlyActions)
                    .build());
        }

        // 12개월 차: 만기 수령 및 자금 재투입
        List<String> month12Actions = new ArrayList<>();
        for (Product p : products) {
            month12Actions.add(p.getProductName() + " 만기 해지 및 원리금 수령");
        }
        month12Actions.add("수령된 원리금 기반 다음 기수 포트폴리오 재투입(리밸런싱)");

        schedules.add(RoadmapResponseDto.MonthlyScheduleDto.builder()
                .month(12)
                .title("만기 달성 및 자금 재투입")
                .actionItems(month12Actions)
                .build());

        return RoadmapResponseDto.builder()
                .style(request.getSelectedStyle())
                .monthlySchedules(schedules)
                .build();
    }
}