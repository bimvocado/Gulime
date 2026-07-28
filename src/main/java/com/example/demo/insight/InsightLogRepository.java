package com.example.demo.insight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InsightLogRepository extends JpaRepository<InsightLog, Long> {

    @Query("SELECT i.abandonedConditionType, COUNT(i) FROM InsightLog i GROUP BY i.abandonedConditionType ORDER BY COUNT(i) DESC")
    List<Object[]> countAbandonmentByConditionType();
}
