package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.entity.ChatErrorType;
import com.gong9ri.gong9ri.entity.ChatInteractionLog;
import com.gong9ri.gong9ri.entity.ChatMessage;
import com.gong9ri.gong9ri.entity.ChatSession;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.ChatInteractionLogRepository;
import com.gong9ri.gong9ri.repository.ChatMessageRepository;
import com.gong9ri.gong9ri.repository.ChatSessionRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@code ChatLogRecorder}는 REQUIRES_NEW로 즉시 커밋하기 때문에(docs/dev/ai/buyer-chatbot/design.md),
 * 클래스 레벨 {@code @Transactional} 롤백에 기대지 않고 매 테스트 뒤 직접 정리한다
 * ({@code AiProductSuggestionServiceTest}와 동일한 이유).
 */
@SpringBootTest
class ChatLogRecorderTest {

    @Autowired
    private ChatLogRecorder chatLogRecorder;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatInteractionLogRepository chatInteractionLogRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member buyer;
    private Member otherBuyer;

    @BeforeEach
    void setUp() {
        buyer = memberRepository.save(new Member("chatLogBuyer1", "pw", "구매자", "chatLogBuyer1@test.com",
                Role.BUYER));
        otherBuyer = memberRepository.save(new Member("chatLogBuyer2", "pw", "구매자2", "chatLogBuyer2@test.com",
                Role.BUYER));
    }

    @AfterEach
    void cleanUp() {
        chatSessionRepository.findAll().stream()
                .filter(s -> s.getBuyer().getId().equals(buyer.getId()) || s.getBuyer().getId().equals(
                        otherBuyer.getId()))
                .forEach(s -> {
                    chatInteractionLogRepository.findAllBySessionId(s.getId())
                            .forEach(log -> chatInteractionLogRepository.deleteById(log.getId()));
                    chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(s.getId())
                            .forEach(m -> chatMessageRepository.deleteById(m.getId()));
                    chatSessionRepository.deleteById(s.getId());
                });
        memberRepository.deleteById(otherBuyer.getId());
        memberRepository.deleteById(buyer.getId());
    }

    @Test
    @DisplayName("sessionId가 없으면 새 세션을 만든다")
    void getOrCreateSession_noSessionId_createsNew() {
        ChatSession session = chatLogRecorder.getOrCreateSession(buyer, null);

        assertEquals(buyer.getId(), session.getBuyer().getId());
    }

    @Test
    @DisplayName("유효한 sessionId면 그 세션을 그대로 재사용한다")
    void getOrCreateSession_validSessionId_reusesSame() {
        ChatSession created = chatLogRecorder.getOrCreateSession(buyer, null);

        ChatSession reused = chatLogRecorder.getOrCreateSession(buyer, created.getId());

        assertEquals(created.getId(), reused.getId());
    }

    @Test
    @DisplayName("30분 넘게 지난 세션이면 새 세션을 만든다")
    void getOrCreateSession_expiredSession_createsNew() {
        ChatSession created = chatLogRecorder.getOrCreateSession(buyer, null);
        ReflectionTestUtils.setField(created, "lastMessageAt", LocalDateTime.now().minusMinutes(31));
        chatSessionRepository.save(created);

        ChatSession result = chatLogRecorder.getOrCreateSession(buyer, created.getId());

        assertNotEquals(created.getId(), result.getId());
    }

    @Test
    @DisplayName("다른 구매자의 세션에 접근하면 FORBIDDEN이다")
    void getOrCreateSession_notOwner_throwsForbidden() {
        ChatSession created = chatLogRecorder.getOrCreateSession(buyer, null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatLogRecorder.getOrCreateSession(otherBuyer, created.getId()));
        assertEquals("FORBIDDEN", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("존재하지 않는 sessionId면 CHAT_SESSION_NOT_FOUND다")
    void getOrCreateSession_unknownSessionId_throwsNotFound() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatLogRecorder.getOrCreateSession(buyer, 999_999_999L));
        assertEquals("CHAT_SESSION_NOT_FOUND", exception.getErrorCode().name());
    }

    @Test
    @DisplayName("성공 턴을 기록하면 메시지 2건과 성공 로그 1건이 남는다")
    void recordSuccessTurn_savesMessagesAndLog() {
        ChatSession session = chatLogRecorder.getOrCreateSession(buyer, null);

        chatLogRecorder.recordSuccessTurn(session.getId(), "질문", "답변", "gpt-4o-mini", 1200L, 50, 30, 80);

        List<ChatMessage> messages = chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.getId());
        assertEquals(2, messages.size());
        List<ChatInteractionLog> logs = chatInteractionLogRepository.findAllBySessionId(session.getId());
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getSuccess());
        assertEquals(80, logs.get(0).getTotalTokens());
    }

    @Test
    @DisplayName("실패 턴을 기록하면 사용자 메시지 1건과 실패 로그 1건만 남는다(어시스턴트 메시지 없음)")
    void recordFailureTurn_savesUserMessageAndFailureLogOnly() {
        ChatSession session = chatLogRecorder.getOrCreateSession(buyer, null);

        chatLogRecorder.recordFailureTurn(session.getId(), "질문", "gpt-4o-mini", 500L, ChatErrorType.TIMEOUT);

        List<ChatMessage> messages = chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.getId());
        assertEquals(1, messages.size());
        List<ChatInteractionLog> logs = chatInteractionLogRepository.findAllBySessionId(session.getId());
        assertEquals(1, logs.size());
        assertTrue(!logs.get(0).getSuccess());
        assertEquals(ChatErrorType.TIMEOUT, logs.get(0).getErrorType());
    }
}
