package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.entity.ChatErrorType;
import com.gong9ri.gong9ri.entity.ChatInteractionLog;
import com.gong9ri.gong9ri.entity.ChatMessage;
import com.gong9ri.gong9ri.entity.ChatRole;
import com.gong9ri.gong9ri.entity.ChatSession;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.repository.ChatInteractionLogRepository;
import com.gong9ri.gong9ri.repository.ChatMessageRepository;
import com.gong9ri.gong9ri.repository.ChatSessionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 챗봇의 DB 쓰기를 전담하는 빈 — 전부 {@code REQUIRES_NEW}로 격리한다. 이유가 이번엔 이중이다:
 * (1) {@code AiSuggestionLogRecorder}/{@code NotificationService}에서 이미 두 번 겪은 "실패를 잡아
 * 로그를 저장한 뒤 예외를 다시 던지면 롤백에 로그까지 같이 사라지는" 문제를 피하기 위해서고,
 * (2) SSE 스트리밍은 컨트롤러 요청 스레드가 즉시 반환된 뒤 Reactor 스케줄러 스레드에서 비동기로
 * 이어지기 때문에, 애초에 기대야 할 "앰비언트 트랜잭션" 자체가 없다 — 그래서 여기 메서드들은
 * 매번 스스로 새 트랜잭션을 여는 것이 선택이 아니라 필수다.
 */
@Service
@RequiredArgsConstructor
public class ChatLogRecorder {

    // 세션 만료(발제 예시값 그대로) — 마지막 대화로부터 30분 지나면 새 세션을 만든다.
    private static final Duration SESSION_EXPIRY = Duration.ofMinutes(30);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatInteractionLogRepository chatInteractionLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatSession getOrCreateSession(Member buyer, Long requestedSessionId) {
        if (requestedSessionId == null) {
            return chatSessionRepository.save(new ChatSession(buyer));
        }

        ChatSession session = chatSessionRepository.findById(requestedSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        if (!session.getBuyer().getId().equals(buyer.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (Duration.between(session.getLastMessageAt(), LocalDateTime.now()).compareTo(SESSION_EXPIRY) >= 0) {
            return chatSessionRepository.save(new ChatSession(buyer));
        }
        return session;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessTurn(Long sessionId, String userContent, String assistantContent, String model,
            long latencyMs, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        ChatSession session = chatSessionRepository.findById(sessionId).orElseThrow();
        chatMessageRepository.save(new ChatMessage(session, ChatRole.USER, userContent));
        chatMessageRepository.save(new ChatMessage(session, ChatRole.ASSISTANT, assistantContent));
        chatInteractionLogRepository.save(ChatInteractionLog.success(
                session, model, latencyMs, promptTokens, completionTokens, totalTokens));
        session.touch();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailureTurn(Long sessionId, String userContent, String model, long latencyMs,
            ChatErrorType errorType) {
        ChatSession session = chatSessionRepository.findById(sessionId).orElseThrow();
        chatMessageRepository.save(new ChatMessage(session, ChatRole.USER, userContent));
        chatInteractionLogRepository.save(ChatInteractionLog.failure(session, model, latencyMs, errorType));
        session.touch();
    }
}
