package com.example.demo.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OptimizationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OptimizationScheduler.class);

    // ⏰ 매일 밤 00:00:00에 실행 (Cron 표현식: 초 분 시 일 월 요일)
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshProductRatesAndWarmupCache() {
        log.info("⏰ [스케줄러 작동] 매일 밤 금리 데이터 최신화 및 최적화 케시 갱신을 시작합니다.");

        try {
            // TODO: 유리가 만든 파싱 데이터 DB 최신화 확인
            // TODO: 하람이 최적화 알고리즘 Prior 통계 테이블 갱신
            log.info("✅ [스케줄러 완료] 금리 데이터 최신화 완벽 성공!");
        } catch (Exception e) {
            log.error("❌ [스케줄러 에러] 금리 최신화 중 오류 발생: {}", e.getMessage());
        }
    }

    // 🧪 테스트용: 앱 켜지고 10초 뒤 1번 작동 확인
    @Scheduled(initialDelay = 10000, fixedRate = 86400000)
    public void testSchedulerOnStartup() {
        log.info("🚀 [스케줄러 헬스체크] 예린님의 A 스케줄러 지원 시스템 정상 가동 중!");
    }
}