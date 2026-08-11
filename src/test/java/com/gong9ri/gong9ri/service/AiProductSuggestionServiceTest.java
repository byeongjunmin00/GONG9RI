package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ProductAiSuggestion;
import com.gong9ri.gong9ri.dto.ProductAiSuggestionRequest;
import com.gong9ri.gong9ri.entity.AiSuggestionLog;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PromptCategory;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.AiSuggestionLogRepository;
import com.gong9ri.gong9ri.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code AiProductSuggestionService}를 실제 OpenAI 호출 없이 검증한다 — {@code ChatClient.Builder}를
 * {@code @MockitoBean}으로 대체해 카테고리별 프롬프트 분기·구조화 응답 파싱·로그 저장·실패 시 에러코드를
 * 확인한다(docs/dev/ai/product-suggestion/design.md). CI에 API 키가 없고 실제 호출은 비용이 들어
 * 이 테스트에서는 절대 진짜 OpenAI를 부르지 않는다.
 */
@SpringBootTest
class AiProductSuggestionServiceTest {

    @Autowired
    private AiProductSuggestionService aiProductSuggestionService;

    @Autowired
    private AiSuggestionLogRepository aiSuggestionLogRepository;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private ChatClient.Builder chatClientBuilder;

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    private Member seller;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        seller = memberRepository.save(new Member("aiSuggestSeller1", "pw", "판매자", "aiSuggestSeller1@test.com",
                Role.SELLER));
    }

    @AfterEach
    void cleanUp() {
        aiSuggestionLogRepository.findAll().stream()
                .filter(logEntry -> logEntry.getSeller().getId().equals(seller.getId()))
                .forEach(logEntry -> aiSuggestionLogRepository.deleteById(logEntry.getId()));
        memberRepository.deleteById(seller.getId());
    }

    private ChatResponse fakeChatResponse(String json, int promptTokens, int completionTokens) {
        Generation generation = new Generation(new AssistantMessage(json));
        DefaultUsage usage = new DefaultUsage(promptTokens, completionTokens);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(usage).build();
        return new ChatResponse(List.of(generation), metadata);
    }

    @Test
    @DisplayName("FOOD 카테고리로 요청하면 구조화된 제안을 반환하고 성공 로그를 남긴다")
    void suggest_food_success() {
        String json = """
                {"suggestedName":"제주 감귤 5kg","suggestedDescription":"신선한 제주 감귤, 유통기한 7일, 5kg 박스",
                "suggestedBasePrice":25000,"suggestedMaxParticipants":10}""";
        when(callResponseSpec.chatResponse()).thenReturn(fakeChatResponse(json, 120, 60));

        ProductAiSuggestion result = aiProductSuggestionService.suggest(
                new MemberUserDetails(seller),
                new ProductAiSuggestionRequest(PromptCategory.FOOD, "제주 감귤 5kg 만원 정도"));

        assertEquals("제주 감귤 5kg", result.suggestedName());
        assertEquals(25000, result.suggestedBasePrice());
        assertEquals(10, result.suggestedMaxParticipants());

        List<AiSuggestionLog> logs = aiSuggestionLogRepository.findAll().stream()
                .filter(logEntry -> logEntry.getSeller().getId().equals(seller.getId()))
                .toList();
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getSuccess());
        assertEquals(180, logs.get(0).getTotalTokens());
    }

    @Test
    @DisplayName("FOOD와 GENERAL 카테고리는 서로 다른 프롬프트로 호출된다")
    void suggest_differentCategories_useDifferentPrompts() {
        String json = """
                {"suggestedName":"a","suggestedDescription":"b","suggestedBasePrice":1000,"suggestedMaxParticipants":10}""";
        when(callResponseSpec.chatResponse()).thenReturn(fakeChatResponse(json, 10, 10));

        aiProductSuggestionService.suggest(new MemberUserDetails(seller),
                new ProductAiSuggestionRequest(PromptCategory.FOOD, "입력A"));
        aiProductSuggestionService.suggest(new MemberUserDetails(seller),
                new ProductAiSuggestionRequest(PromptCategory.GENERAL, "입력A"));

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(requestSpec, org.mockito.Mockito.times(2)).user(promptCaptor.capture());
        List<String> prompts = promptCaptor.getAllValues();
        assertTrue(prompts.get(0).contains("신선식품") || prompts.get(0).contains("유통기한"),
                "FOOD 프롬프트는 신선식품 관련 지시가 포함돼야 한다");
        assertTrue(!prompts.get(0).equals(prompts.get(1)), "카테고리별 프롬프트 내용이 달라야 한다");
    }

    @Test
    @DisplayName("LLM 호출이 실패하면 AI_SUGGESTION_FAILED로 응답하고 실패 로그를 남긴다")
    void suggest_llmFailure_throwsBusinessExceptionAndLogsFailure() {
        when(callResponseSpec.chatResponse()).thenThrow(new RuntimeException("OpenAI 5xx"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                aiProductSuggestionService.suggest(new MemberUserDetails(seller),
                        new ProductAiSuggestionRequest(PromptCategory.GENERAL, "아무거나")));

        assertEquals("AI_SUGGESTION_FAILED", exception.getErrorCode().name());

        List<AiSuggestionLog> logs = aiSuggestionLogRepository.findAll().stream()
                .filter(logEntry -> logEntry.getSeller().getId().equals(seller.getId()))
                .toList();
        assertEquals(1, logs.size());
        assertTrue(!logs.get(0).getSuccess());
    }

    @Test
    @DisplayName("구매자 계정으로 요청하면 403 FORBIDDEN이다")
    void suggest_buyerRole_throwsForbidden() {
        Member buyer = memberRepository.save(new Member("aiSuggestBuyer1", "pw", "구매자", "aiSuggestBuyer1@test.com",
                Role.BUYER));
        try {
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    aiProductSuggestionService.suggest(new MemberUserDetails(buyer),
                            new ProductAiSuggestionRequest(PromptCategory.GENERAL, "아무거나")));
            assertEquals("FORBIDDEN", exception.getErrorCode().name());
        } finally {
            memberRepository.deleteById(buyer.getId());
        }
    }
}
