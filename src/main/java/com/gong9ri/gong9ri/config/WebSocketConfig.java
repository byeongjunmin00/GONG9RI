package com.gong9ri.gong9ri.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 실시간 메시징(발제 도전과제) — 공구팀 정원 변동을 참여자 전원에게 브로드캐스트하는 1:N 채널.
 * 이미 있는 SSE(구매자 챗봇)는 1:1 스트림이라 이 용도로 구조적으로 안 맞아서 별도 도입한다.
 * 별도 메시지 브로커 인프라 없이 인메모리 심플 브로커만 쓴다 — 이 규모(2인 팀, 단일 인스턴스)에
 * 과한 설비. SockJS 폴백도 안 씀(Railway가 표준 WebSocket 업그레이드를 지원하는 걸 전제, 레거시
 * 브라우저/사내망 프록시 대응은 스코프 밖 — docs/dev/team/crud/design.md 참고).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-team");
    }
}
