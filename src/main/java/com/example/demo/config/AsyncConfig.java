package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);        // 기본 동시 계산 쓰레드 수
        executor.setMaxPoolSize(10);       // 최대 확장 쓰레드 수
        executor.setQueueCapacity(100);    // 대기열 크기
        executor.setThreadNamePrefix("harams-optimizer-");
        executor.initialize();
        return executor;
    }
}