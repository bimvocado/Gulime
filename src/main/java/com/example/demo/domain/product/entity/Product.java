package com.example.demo.domain.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @Column(name = "product_id", length = 50)
    private String productId;

    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "product_type", nullable = false, length = 20)
    private String productType; // SAVING, DEPOSIT, PARKING

    @Column(name = "base_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal baseRate;

    @Column(name = "max_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal maxRate;

    @Column(name = "max_limit")
    private Long maxLimit;

    @Column(name = "period_months", nullable = false)
    private Integer periodMonths;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified;

    @Column(name = "selection_rule", length = 30)
    private String selectionRule;

    @Column(name = "max_select")
    private Integer maxSelect;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductCondition> conditions = new ArrayList<>();
}