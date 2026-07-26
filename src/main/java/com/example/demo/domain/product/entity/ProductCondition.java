package com.example.demo.domain.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product_conditions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "condition_id")
    private Long conditionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "period_months")
    private Integer periodMonths;

    @Column(name = "required_months")
    private Integer requiredMonths;

    @Column(name = "selection_type")
    private String selectionType; // "TIERED"(단계형), "CHOICE"(선택형), "SINGLE"(단일) 등

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "resource", nullable = false, length = 30)
    private String resource;

    @Column(name = "threshold")
    private Long threshold;

    @Column(name = "rate_bonus", nullable = false, precision = 4, scale = 2)
    private BigDecimal rateBonus;

    @Column(name = "payout_type", nullable = false, length = 30)
    private String payoutType;

    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;
}