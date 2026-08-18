package com.gong9ri.gong9ri.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 실시간 메시징(발제 도전과제) — 공구팀 정원 변동을 참여자 전원에게 브로드캐스트하는 1:N 채널.
 * 이미 있는 SSE(구매자 챗봇)는 1:1 스트림이라 이 용도로 구조적으로 안 맞아서 별도 도입한다.
 * 별도 메시지 브로커 인프라 없이 인메모리 심플 브로커만 쓴다 — 이 규모(2인 팀, 단일 인스턴스)에
 * 과한 설비. SockJS 폴백도 안 씀(Railway가 표준 WebSocket 업그레이드를 지원하는 걸 전제, 레거시
 * 브라우저/사내망 프록시 대응은 스코프 밖 — docs/dev/team/crud/design.md 참고).
 *
 * <p><b>프로덕션 반복 OOM 재발(2026-08-19) 대응</b>: 명시적으로 스레드풀 크기를 안 지정하면 Spring이
 * 브로커용 태스크 스케줄러·클라이언트 인바운드/아웃바운드 채널 executor를 기본 설정(무제한에 가까운
 * max)으로 만든다 — `railway ssh`로 프로덕션 컨테이너에 직접 들어가 실측한 결과, "MessageBroker-1~4"
 * 이름의 스레드가 각각 11개씩(총 140개 스레드 중 48개) 쌓여있는 걸 확인했다(스레드 하나당
 * Dockerfile의 `-Xss512k` 스택 + glibc malloc arena 오버헤드가 붙어, 이 정도 규모로도 메모리를
 * 상당히 잠식한다 — `docs/logs/cd/deploy/003-oom-crash.md`의 미해결 후속 항목). 이 앱 규모(2인팀,
 * 단일 인스턴스, WebSocket 브로드캐스트는 팀 정원 변경 알림뿐)에 맞게 전부 작은 고정 크기로 못박는다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-broker-");
        scheduler.initialize();

        registry.enableSimpleBroker("/topic").setTaskScheduler(scheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-team");
    }
}
