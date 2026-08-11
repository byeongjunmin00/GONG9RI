package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.entity.AiSuggestionLog;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PromptCategory;
import com.gong9ri.gong9ri.repository.AiSuggestionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AiSuggestionLog} 저장 전용 — {@code AiProductSuggestionService.suggest()}가 LLM 호출
 * 실패를 잡아서 예외를 다시 던지는데, 만약 그 저장이 같은 트랜잭션 안에 있으면 예외가 트랜잭션을
 * 롤백 대상으로 표시해서 실패 로그 저장 자체가 사라진다(실제로 겪은 버그 — {@code NotificationService}의
 * REQUIRES_NEW와 같은 이유). 별도 빈으로 분리하고 {@code REQUIRES_NEW}를 써야, 같은 클래스 안에서
 * private 메서드로 나눴을 때 발생하는 "self-invocation으로 프록시를 안 거쳐 애노테이션이 무시되는" 문제도
 * 같이 피한다.
 */
@Service
@RequiredArgsConstructor
public class AiSuggestionLogRecorder {

    private final AiSuggestionLogRepository aiSuggestionLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Member seller, PromptCategory category, String inputText,
            String suggestedName, String suggestedDescription, Integer suggestedBasePrice,
            Integer suggestedMaxParticipants, Integer promptTokens, Integer completionTokens, Integer totalTokens,
            long latencyMs) {
        aiSuggestionLogRepository.save(AiSuggestionLog.success(seller, category, inputText,
                suggestedName, suggestedDescription, suggestedBasePrice, suggestedMaxParticipants,
                promptTokens, completionTokens, totalTokens, latencyMs));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Member seller, PromptCategory category, String inputText, String errorMessage,
            long latencyMs) {
        aiSuggestionLogRepository.save(AiSuggestionLog.failure(seller, category, inputText, errorMessage, latencyMs));
    }
}
