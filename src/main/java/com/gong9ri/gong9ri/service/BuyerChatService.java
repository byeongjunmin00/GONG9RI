package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ChatMessageRequest;
import com.gong9ri.gong9ri.dto.ChatMessageResponse;
import com.gong9ri.gong9ri.dto.ChatSessionUsageResponse;
import com.gong9ri.gong9ri.dto.ChatStatsResponse;
import com.gong9ri.gong9ri.entity.ChatErrorType;
import com.gong9ri.gong9ri.entity.ChatInteractionLog;
import com.gong9ri.gong9ri.entity.ChatMessage;
import com.gong9ri.gong9ri.entity.ChatRole;
import com.gong9ri.gong9ri.entity.ChatSession;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.ChatInteractionLogRepository;
import com.gong9ri.gong9ri.repository.ChatMessageRepository;
import com.gong9ri.gong9ri.repository.ChatSessionRepository;
import com.gong9ri.gong9ri.repository.ProductRepository;
import com.gong9ri.gong9ri.repository.TeamParticipationRepository;
import com.openai.errors.InternalServerException;
import com.openai.errors.RateLimitException;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 구매자 챗봇(발제 AI 필수2 Tool Calling + 필수3 장애격리·비용인식 + 도전 SSE 스트리밍·멀티턴).
 * 설계 근거는 docs/dev/ai/buyer-chatbot/design.md 참고.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuyerChatService {

    // LLM 응답 대기 상한 — 실측 근거 없는 초기값(design.md에 명시, refund-trigger 1분 주기와 같은 성격).
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(15);
    // SseEmitter 자체의 연결 유지 상한(위 LLM_TIMEOUT과는 별개 — 연결 안전장치).
    private static final long EMITTER_TIMEOUT_MS = 60_000L;

    private static final String SYSTEM_PROMPT = """
            너는 공동구매 플랫폼 GONG9RI의 구매자 전용 채팅 상담원이다. 구매자가 상품이나 본인의 공구
            참여 현황처럼 실시간 데이터가 필요한 질문을 하면, 반드시 제공된 도구(searchProducts,
            getMyTeamParticipations)를 호출해서 실제 데이터를 확인한 뒤에 답해라. 도구로도, 아래
            제공되는 정책 문서로도 확인 안 된 사실을 추측해서 답하지 마라 — 확실하지 않으면 모른다고
            솔직히 답해라. 반대로 아래에 관련된 정책 문서 스니펫이 제공되면, 그건 이미 확인된 사실이니
            망설이지 말고 그 내용을 그대로 답변 근거로 사용해라. GONG9RI 공동구매 서비스와 무관한
            질문에는 답하지 말고, 이 서비스와 관련된 것만 도와줄 수 있다고 안내해라.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatInteractionLogRepository chatInteractionLogRepository;
    private final ProductRepository productRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final ChatLogRecorder chatLogRecorder;
    private final PolicyRagService policyRagService;

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(MemberUserDetails principal, Long sessionId) {
        requireBuyer(principal);
        requireOwnSession(principal.getMember(), sessionId);
        return chatMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatSessionUsageResponse sessionUsage(MemberUserDetails principal, Long sessionId) {
        requireBuyer(principal);
        requireOwnSession(principal.getMember(), sessionId);
        long totalTokens = chatInteractionLogRepository.findAllBySessionId(sessionId).stream()
                .filter(ChatInteractionLog::getSuccess)
                .mapToLong(logEntry -> logEntry.getTotalTokens() != null ? logEntry.getTotalTokens() : 0L)
                .sum();
        return new ChatSessionUsageResponse(sessionId, totalTokens);
    }

    // 모델별 누적 토큰·P95 응답지연·에러율 대시보드(발제 AI 필수3 요구사항). 이 프로젝트 데이터 규모에서는
    // DB 퍼센타일 함수 대신 로그를 모델별로 모아 애플리케이션에서 정렬 후 계산하는 편이 단순하다.
    @Transactional(readOnly = true)
    public List<ChatStatsResponse> stats() {
        Map<String, List<ChatInteractionLog>> byModel = chatInteractionLogRepository.findAll().stream()
                .collect(Collectors.groupingBy(ChatInteractionLog::getModel));

        return byModel.entrySet().stream()
                .map(entry -> toStatsResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private ChatStatsResponse toStatsResponse(String model, List<ChatInteractionLog> logs) {
        long callCount = logs.size();
        long totalTokens = logs.stream()
                .filter(ChatInteractionLog::getSuccess)
                .mapToLong(l -> l.getTotalTokens() != null ? l.getTotalTokens() : 0L)
                .sum();
        long errorCount = logs.stream().filter(l -> !l.getSuccess()).count();
        double errorRate = callCount == 0 ? 0.0 : (double) errorCount / callCount;

        List<Long> sortedLatencies = logs.stream()
                .map(ChatInteractionLog::getLatencyMs)
                .sorted(Comparator.naturalOrder())
                .toList();
        long p95LatencyMs = percentile95(sortedLatencies);

        return new ChatStatsResponse(model, callCount, totalTokens, p95LatencyMs, errorRate);
    }

    private long percentile95(List<Long> sortedLatencies) {
        if (sortedLatencies.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
        return sortedLatencies.get(Math.max(0, Math.min(index, sortedLatencies.size() - 1)));
    }

    private ChatSession requireOwnSession(Member buyer, Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        if (!session.getBuyer().getId().equals(buyer.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    public SseEmitter streamChat(MemberUserDetails principal, ChatMessageRequest request) {
        requireBuyer(principal);
        Member buyer = principal.getMember();

        // 세션 조회/생성은 스트림 시작 전, 원래 요청 스레드에서 동기로 처리한다 — 여기서 발생하는
        // 권한/존재 오류(BusinessException)는 SSE 프레임이 아니라 평범한 HTTP 에러 응답으로 나가야 하므로,
        // SseEmitter를 만들기 전에 먼저 던져지게 한다.
        ChatSession session = chatLogRecorder.getOrCreateSession(buyer, request.sessionId());
        List<Message> history = loadRecentHistory(session.getId());
        ChatbotTools tools = new ChatbotTools(buyer.getId(), productRepository, teamParticipationRepository);
        List<String> ragSnippets = retrieveRagContext(request.content());

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        sendEvent(emitter, "session", String.valueOf(session.getId()));

        StringBuilder assembled = new StringBuilder();
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        long startedAt = System.currentTimeMillis();

        chatClientBuilder.build()
                .prompt()
                .system(buildSystemPrompt(ragSnippets))
                .messages(history)
                .user(request.content())
                .tools(tools)
                .options(buildChatOptions())
                .stream()
                .chatResponse()
                .timeout(LLM_TIMEOUT)
                .subscribe(
                        chatResponse -> onChunk(emitter, assembled, usageRef, chatResponse),
                        throwable -> onError(emitter, session.getId(), request.content(), startedAt, throwable),
                        () -> onComplete(emitter, session.getId(), request.content(), assembled, startedAt,
                                usageRef.get()));

        return emitter;
    }

    private void onChunk(SseEmitter emitter, StringBuilder assembled, AtomicReference<Usage> usageRef,
            ChatResponse chatResponse) {
        String text = chatResponse.getResult() != null ? chatResponse.getResult().getOutput().getText() : null;
        if (text != null && !text.isEmpty()) {
            assembled.append(text);
            sendEvent(emitter, "message", text);
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage != null && usage.getTotalTokens() != null) {
            usageRef.set(usage);
        }
    }

    private void onComplete(SseEmitter emitter, Long sessionId, String userContent, StringBuilder assembled,
            long startedAt, Usage usage) {
        long latencyMs = System.currentTimeMillis() - startedAt;
        Integer promptTokens = usage != null ? usage.getPromptTokens() : null;
        Integer completionTokens = usage != null ? usage.getCompletionTokens() : null;
        Integer totalTokens = usage != null ? usage.getTotalTokens() : null;
        chatLogRecorder.recordSuccessTurn(sessionId, userContent, assembled.toString(), "gpt-4o-mini", latencyMs,
                promptTokens, completionTokens, totalTokens);
        sendEvent(emitter, "done", "");
        emitter.complete();
    }

    private void onError(SseEmitter emitter, Long sessionId, String userContent, long startedAt,
            Throwable throwable) {
        long latencyMs = System.currentTimeMillis() - startedAt;
        ChatErrorType errorType = classifyError(throwable);
        log.error("챗봇 응답 실패: sessionId={}, errorType={}, latencyMs={}", sessionId, errorType, latencyMs,
                throwable);
        chatLogRecorder.recordFailureTurn(sessionId, userContent, "gpt-4o-mini", latencyMs, errorType);
        sendEvent(emitter, "message", fallbackMessage(errorType));
        sendEvent(emitter, "done", "");
        emitter.complete();
    }

    private ChatErrorType classifyError(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            return ChatErrorType.TIMEOUT;
        }
        if (throwable instanceof RateLimitException) {
            return ChatErrorType.RATE_LIMIT;
        }
        if (throwable instanceof InternalServerException) {
            return ChatErrorType.SERVER_ERROR;
        }
        return ChatErrorType.OTHER;
    }

    // Fallback 최소 2가지(발제 요구사항): 타임아웃과 그 외 API 장애(RateLimit/5xx/기타)를 구분해서 안내한다.
    // 에러 유형 자체는 ChatErrorType으로 4종 세분화해 로그에 남기지만(대시보드 breakdown용),
    // 사용자에게 보여주는 문구는 두 갈래로 충분하다고 판단.
    private String fallbackMessage(ChatErrorType errorType) {
        if (errorType == ChatErrorType.TIMEOUT) {
            return "지금 응답이 지연되고 있어요. 잠시 후 다시 시도해주세요.";
        }
        return "일시적으로 AI 상담이 어려워요. 잠시 후 다시 시도해주세요.";
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            log.warn("SSE 전송 실패, 연결이 이미 끊어졌을 수 있음: {}", e.getMessage());
        }
    }

    // RAG(전용운, ai/policy-rag)는 관련성을 걸러주지 않고 항상 topK를 반환한다(PolicyRagService 계약) —
    // 관련 없는 문맥이 섞여 있을 수 있다는 전제로 시스템 프롬프트에서 "무관하면 무시하라"고 지시한다.
    // 검색 자체가 실패해도(임베딩 API 장애 등) 챗봇 턴 전체를 실패시키지 않고 컨텍스트 없이 진행한다 —
    // RAG는 답변을 보강하는 부가 기능이지, 챗봇이 동작하기 위한 필수 전제가 아니다.
    private List<String> retrieveRagContext(String query) {
        try {
            return policyRagService.findRelevantSnippets(query);
        } catch (Exception e) {
            log.warn("정책 RAG 검색 실패, 컨텍스트 없이 진행: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSystemPrompt(List<String> ragSnippets) {
        if (ragSnippets.isEmpty()) {
            return SYSTEM_PROMPT;
        }
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT);
        prompt.append("\n\n다음은 참고할 수 있는 정책 문서 스니펫이다. 질문과 관련이 없으면 참고하지 말고 무시해라:\n\n");
        for (String snippet : ragSnippets) {
            prompt.append(snippet).append("\n\n---\n\n");
        }
        return prompt.toString();
    }

    private List<Message> loadRecentHistory(Long sessionId) {
        List<ChatMessage> recent = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(recent);
        return recent.stream()
                .<Message>map(m -> m.getRole() == ChatRole.USER
                        ? new UserMessage(m.getContent())
                        : new AssistantMessage(m.getContent()))
                .toList();
    }

    private OpenAiChatOptions.Builder buildChatOptions() {
        return OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.3);
    }

    private void requireBuyer(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.BUYER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
