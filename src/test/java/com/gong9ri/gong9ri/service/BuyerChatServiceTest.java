package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ChatMessageRequest;
import com.gong9ri.gong9ri.entity.ChatInteractionLog;
import com.gong9ri.gong9ri.entity.ChatMessage;
import com.gong9ri.gong9ri.entity.ChatRole;
import com.gong9ri.gong9ri.entity.ChatSession;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.ChatInteractionLogRepository;
import com.gong9ri.gong9ri.repository.ChatMessageRepository;
import com.gong9ri.gong9ri.repository.ChatSessionRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.openai.errors.RateLimitException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.gong9ri.gong9ri.entity.Product;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * {@code ChatClient.Builder}를 목으로 대체해 실제 OpenAI 호출 없이 스트리밍·폴백·토큰기록·N턴윈도잉을
 * 검증한다. 실제 Reactor 15초 타임아웃을 기다리는 대신, 타임아웃이 났을 때와 같은 예외를 스트림에
 * 직접 흘려보내 분류·폴백 로직만 검증한다(docs/dev/ai/buyer-chatbot/design.md).
 */
@SpringBootTest
class BuyerChatServiceTest {

    @Autowired
    private BuyerChatService buyerChatService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatInteractionLogRepository chatInteractionLogRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @MockitoBean
    private ChatClient.Builder chatClientBuilder;

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.StreamResponseSpec streamResponseSpec;

    private Member buyer;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);

        buyer = memberRepository.save(new Member("chatSvcBuyer1", "pw", "구매자", "chatSvcBuyer1@test.com",
                Role.BUYER));
    }

    @AfterEach
    void cleanUp() {
        chatSessionRepository.findAll().stream()
                .filter(s -> s.getBuyer().getId().equals(buyer.getId()))
                .forEach(s -> {
                    chatInteractionLogRepository.findAllBySessionId(s.getId())
                            .forEach(log -> chatInteractionLogRepository.deleteById(log.getId()));
                    chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(s.getId())
                            .forEach(m -> chatMessageRepository.deleteById(m.getId()));
                    chatSessionRepository.deleteById(s.getId());
                });
        memberRepository.deleteById(buyer.getId());
    }

    private ChatResponse chunkWithUsage(String text, Integer promptTokens, Integer completionTokens) {
        Generation generation = new Generation(new AssistantMessage(text));
        ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder();
        if (promptTokens != null) {
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return new ChatResponse(List.of(generation), metadataBuilder.build());
    }

    private void awaitLogRow(Long sessionId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (!chatInteractionLogRepository.findAllBySessionId(sessionId).isEmpty()) {
                return;
            }
            Thread.sleep(20);
        }
    }

    @Test
    @DisplayName("성공하면 청크가 합쳐진 답변과 토큰 사용량이 기록된다")
    void streamChat_success_recordsMessagesAndLog() throws InterruptedException {
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.just(
                chunkWithUsage("안녕하세요, ", null, null),
                chunkWithUsage("감귤을 찾아드릴게요.", 100, 40)));

        SseEmitter emitter = buyerChatService.streamChat(new MemberUserDetails(buyer),
                new ChatMessageRequest(null, "감귤 있어?"));
        assertTrue(emitter != null);

        List<ChatSession> sessions = chatSessionRepository.findAll().stream()
                .filter(s -> s.getBuyer().getId().equals(buyer.getId())).toList();
        assertEquals(1, sessions.size());
        Long sessionId = sessions.get(0).getId();
        awaitLogRow(sessionId);

        List<ChatMessage> messages = chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);
        assertEquals(2, messages.size());
        assertEquals(ChatRole.USER, messages.get(0).getRole());
        assertEquals("감귤 있어?", messages.get(0).getContent());
        assertEquals(ChatRole.ASSISTANT, messages.get(1).getRole());
        assertEquals("안녕하세요, 감귤을 찾아드릴게요.", messages.get(1).getContent());

        List<ChatInteractionLog> logs = chatInteractionLogRepository.findAllBySessionId(sessionId);
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getSuccess());
        assertEquals(140, logs.get(0).getTotalTokens());
    }

    @Test
    @DisplayName("타임아웃 예외는 TIMEOUT으로 분류되고 사용자 메시지만 남는다")
    void streamChat_timeout_classifiedAsTimeout() throws InterruptedException {
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.error(new TimeoutException("simulated")));

        buyerChatService.streamChat(new MemberUserDetails(buyer), new ChatMessageRequest(null, "질문"));

        Long sessionId = chatSessionRepository.findAll().stream()
                .filter(s -> s.getBuyer().getId().equals(buyer.getId())).findFirst().orElseThrow().getId();
        awaitLogRow(sessionId);

        List<ChatInteractionLog> logs = chatInteractionLogRepository.findAllBySessionId(sessionId);
        assertEquals(1, logs.size());
        assertTrue(!logs.get(0).getSuccess());
        assertEquals("TIMEOUT", logs.get(0).getErrorType().name());
        assertEquals(1, chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId).size());
    }

    @Test
    @DisplayName("RateLimitException은 RATE_LIMIT으로 분류된다")
    void streamChat_rateLimit_classifiedAsRateLimit() throws InterruptedException {
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.error(mock(RateLimitException.class)));

        buyerChatService.streamChat(new MemberUserDetails(buyer), new ChatMessageRequest(null, "질문"));

        Long sessionId = chatSessionRepository.findAll().stream()
                .filter(s -> s.getBuyer().getId().equals(buyer.getId())).findFirst().orElseThrow().getId();
        awaitLogRow(sessionId);

        List<ChatInteractionLog> logs = chatInteractionLogRepository.findAllBySessionId(sessionId);
        assertEquals("RATE_LIMIT", logs.get(0).getErrorType().name());
    }

    @Test
    @DisplayName("분류되지 않는 예외는 OTHER로 기록된다")
    void streamChat_unknownException_classifiedAsOther() throws InterruptedException {
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.error(new RuntimeException("unexpected")));

        buyerChatService.streamChat(new MemberUserDetails(buyer), new ChatMessageRequest(null, "질문"));

        Long sessionId = chatSessionRepository.findAll().stream()
                .filter(s -> s.getBuyer().getId().equals(buyer.getId())).findFirst().orElseThrow().getId();
        awaitLogRow(sessionId);

        List<ChatInteractionLog> logs = chatInteractionLogRepository.findAllBySessionId(sessionId);
        assertEquals("OTHER", logs.get(0).getErrorType().name());
    }

    @Test
    @DisplayName("챗봇 호출이 실패해도 핵심 서비스(상품 저장)는 정상 동작한다")
    void streamChat_failureDoesNotAffectCoreService() throws InterruptedException {
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.error(new TimeoutException("simulated")));
        buyerChatService.streamChat(new MemberUserDetails(buyer), new ChatMessageRequest(null, "질문"));

        Long sessionId = chatSessionRepository.findAll().stream()
                .filter(s -> s.getBuyer().getId().equals(buyer.getId())).findFirst().orElseThrow().getId();
        awaitLogRow(sessionId);

        Product product = productRepository.save(new Product(buyer, "핵심서비스무영향테스트상품", "설명", 1000, 5, null));
        try {
            assertTrue(product.getId() != null);
        } finally {
            productRepository.deleteById(product.getId());
        }
    }

    @Test
    @DisplayName("이전 대화가 10개 넘게 있어도 최근 10개만 시간순으로 프롬프트에 포함된다")
    void streamChat_windowsToLast10MessagesInOrder() throws InterruptedException {
        ChatSession session = chatSessionRepository.save(new ChatSession(buyer));
        for (int i = 1; i <= 12; i++) {
            ChatRole role = (i % 2 == 1) ? ChatRole.USER : ChatRole.ASSISTANT;
            chatMessageRepository.save(new ChatMessage(session, role, "msg-" + i));
        }
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.just(chunkWithUsage("답", 5, 5)));

        buyerChatService.streamChat(new MemberUserDetails(buyer),
                new ChatMessageRequest(session.getId(), "새 질문"));
        awaitLogRow(session.getId());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Message>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(requestSpec).messages(captor.capture());
        List<Message> sentHistory = captor.getValue();

        assertEquals(10, sentHistory.size());
        assertEquals("msg-3", sentHistory.get(0).getText());
        assertEquals("msg-12", sentHistory.get(9).getText());
    }
}
