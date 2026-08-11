package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * LLM 호출 1회(챗봇 한 턴)마다 성공/실패와 무관하게 1행 기록 — 토큰·응답시간·에러율 집계용.
 * 대화 내용 자체는 {@link ChatMessage}가 담당(이 엔티티엔 content 없음, 역할 분리).
 */
@Entity
@Table(name = "chat_interaction_log", indexes = {
        @Index(name = "idx_session", columnList = "session_id"),
        @Index(name = "idx_model", columnList = "model")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ChatInteractionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false)
    private Long latencyMs;

    @Column(nullable = false)
    private Boolean success;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ChatErrorType errorType;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChatInteractionLog success(ChatSession session, String model, long latencyMs,
            Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        ChatInteractionLog log = new ChatInteractionLog();
        log.session = session;
        log.model = model;
        log.latencyMs = latencyMs;
        log.success = true;
        log.promptTokens = promptTokens;
        log.completionTokens = completionTokens;
        log.totalTokens = totalTokens;
        return log;
    }

    public static ChatInteractionLog failure(ChatSession session, String model, long latencyMs,
            ChatErrorType errorType) {
        ChatInteractionLog log = new ChatInteractionLog();
        log.session = session;
        log.model = model;
        log.latencyMs = latencyMs;
        log.success = false;
        log.errorType = errorType;
        return log;
    }
}
