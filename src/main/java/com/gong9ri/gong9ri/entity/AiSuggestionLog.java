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
 * 판매자 상품등록 AI 도우미 호출 1건의 기록(docs/dev/ai/product-suggestion/design.md) — "LLM 응답을
 * 구조화 출력으로 파싱해서 DB 저장" 요구사항을 이 엔티티가 충족한다. 성공/실패 모두 남긴다(실패 시
 * {@code suggested*} 필드는 null, {@code errorMessage}만 채워짐) — 토큰·응답시간도 성공 호출에 한해 기록된다.
 * 하드 삭제 없음(호출 이력·비용 감사 목적).
 */
@Entity
@Table(name = "ai_suggestion_log", indexes = {
        @Index(name = "idx_seller", columnList = "seller_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AiSuggestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromptCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputText;

    @Column(length = 100)
    private String suggestedName;

    @Column(columnDefinition = "TEXT")
    private String suggestedDescription;

    private Integer suggestedBasePrice;

    private Integer suggestedMaxParticipants;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long latencyMs;

    @Column(nullable = false)
    private Boolean success;

    @Column(length = 500)
    private String errorMessage;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private AiSuggestionLog(Member seller, PromptCategory category, String inputText) {
        this.seller = seller;
        this.category = category;
        this.inputText = inputText;
    }

    public static AiSuggestionLog success(Member seller, PromptCategory category, String inputText,
            String suggestedName, String suggestedDescription, Integer suggestedBasePrice,
            Integer suggestedMaxParticipants, Integer promptTokens, Integer completionTokens, Integer totalTokens,
            Long latencyMs) {
        AiSuggestionLog log = new AiSuggestionLog(seller, category, inputText);
        log.suggestedName = suggestedName;
        log.suggestedDescription = suggestedDescription;
        log.suggestedBasePrice = suggestedBasePrice;
        log.suggestedMaxParticipants = suggestedMaxParticipants;
        log.promptTokens = promptTokens;
        log.completionTokens = completionTokens;
        log.totalTokens = totalTokens;
        log.latencyMs = latencyMs;
        log.success = true;
        return log;
    }

    public static AiSuggestionLog failure(Member seller, PromptCategory category, String inputText,
            String errorMessage, Long latencyMs) {
        AiSuggestionLog log = new AiSuggestionLog(seller, category, inputText);
        log.errorMessage = errorMessage;
        log.latencyMs = latencyMs;
        log.success = false;
        return log;
    }
}
