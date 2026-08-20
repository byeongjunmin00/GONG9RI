package com.gong9ri.gong9ri.dto;

/** 상담 메시지 전송 (support/chat). 길이·공백 검증은 서비스가 한다(WebSocket이라 Bean Validation이 안 걸린다). */
public record SupportMessageSendRequest(String content) {
}
