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

    @Column(name = "condition_name")
    private String conditionName;

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

    @Column(name = "hard_requirement")
    @Builder.Default
    private Boolean hardRequirement = false;

    @Column(name = "selectable")
    @Builder.Default
    private Boolean selectable = false;

    @Column(name = "selection_group")
    private String selectionGroup;

    @Column(name = "tier_group")
    private String tierGroup;

    @Column(name = "exclusive_group")
    private String exclusiveGroup;

    @Column(name = "branch")
    private String branch;

    @Column(name = "parse_status", length = 30)
    private String parseStatus;

    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;
}
