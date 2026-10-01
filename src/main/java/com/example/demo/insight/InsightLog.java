package com.example.demo.insight;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "insight_logs")
@Getter @Setter
@NoArgsConstructor
public class InsightLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    private String userSessionId;
    private String selectedProductId;

    // 포기된 조건 정보 (예: CARD_BUDGET, MARKETING_AGREE 등)
    private String abandonedConditionType;
    private String abandonedConditionName;

    // 당시 유저 자원 상태
    private Long userCardBudget;
    private Boolean userSalaryTransfer;

    private LocalDateTime createdAt = LocalDateTime.now();

    public InsightLog(String userSessionId, String selectedProductId, String abandonedConditionType, String abandonedConditionName, Long userCardBudget, Boolean userSalaryTransfer) {
        this.userSessionId = userSessionId;
        this.selectedProductId = selectedProductId;
        this.abandonedConditionType = abandonedConditionType;
        this.abandonedConditionName = abandonedConditionName;
        this.userCardBudget = userCardBudget;
        this.userSalaryTransfer = userSalaryTransfer;
    }
}
