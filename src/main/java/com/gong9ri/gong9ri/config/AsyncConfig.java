package com.gong9ri.gong9ri.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@code @Async} 이벤트 리스너({@code TeamDeadlineEventListener}) 전용 스레드풀.
 * 목적은 딱 하나 — 마감 감지 이벤트 처리(락 대기+DB 쓰기)가 스케줄러의 스캔 루프를 막지 않게 분리하는 것
 * (docs/dev/ongoing/refund-event-messaging.md 태스크 7). 기본 SimpleAsyncTaskExecutor(스레드 매번 새로 생성)
 * 대신 작은 풀을 명시해 무제한 스레드 생성을 막는다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-async-");
        executor.initialize();
        return executor;
    }

    // 예외는 @RestControllerAdvice 없이는 호출자에게 전파되지 않는 @Async void 메서드라
    // 그냥 삼켜지지 않도록 직접 로그로 남긴다(docs/code-convention.md 로깅 규칙).
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return this::logAsyncException;
    }

    private void logAsyncException(Throwable throwable, Method method, Object... params) {
        log.error("비동기 이벤트 처리 중 예외 발생: method={}, params={}", method.getName(), params, throwable);
    }
}
