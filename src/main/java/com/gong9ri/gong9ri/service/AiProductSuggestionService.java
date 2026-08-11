package com.gong9ri.gong9ri.service;

import com.gong9ri.gong9ri.common.exception.BusinessException;
import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.dto.ProductAiSuggestion;
import com.gong9ri.gong9ri.dto.ProductAiSuggestionRequest;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.PromptTemplate;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 상품등록 AI 도우미 (docs/dev/ai/product-suggestion/design.md) — 발제 AI 필수 "구조화 출력 +
 * 프롬프트 엔지니어링" 항목. 판매자가 대충 적은 설명을 구조화된 상품 제안(이름/설명/기본가/최대인원)으로
 * 바꿔주되, 그 값을 바로 DB에 상품으로 저장하지 않는다 — 판매자가 검토 후 기존 상품 등록 API로 직접
 * 등록해야 한다("AI 결과물을 비판적으로 검토" 원칙).
 *
 * <p>이 메서드 자체는 트랜잭션을 열지 않는다(클래스 기본 {@code readOnly=true}만 적용, 프롬프트 템플릿
 * 조회는 읽기 전용으로 충분) — LLM 호출처럼 느린 외부 HTTP 호출을 트랜잭션(=DB 커넥션 점유) 안에 가두지
 * 않기 위해서다. 로그 저장(성공/실패)은 {@link AiSuggestionLogRecorder}(별도 빈, REQUIRES_NEW)에게
 * 위임한다 — 실패 시 이 메서드가 예외를 다시 던지는데, 만약 로그 저장이 이 메서드와 같은 트랜잭션 안에
 * 있었다면 그 예외가 트랜잭션을 롤백 대상으로 표시해 로그 저장 자체가 사라진다(실제로 겪은 버그).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProductSuggestionService {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateRepository promptTemplateRepository;
    private final AiSuggestionLogRecorder aiSuggestionLogRecorder;

    private final BeanOutputConverter<ProductAiSuggestion> outputConverter =
            new BeanOutputConverter<>(ProductAiSuggestion.class);

    @Transactional(readOnly = true)
    public ProductAiSuggestion suggest(MemberUserDetails principal, ProductAiSuggestionRequest request) {
        requireSeller(principal);
        Member seller = principal.getMember();

        PromptTemplate template = promptTemplateRepository.findByCategory(request.category())
                .orElseThrow(() -> new IllegalStateException(
                        "프롬프트 템플릿이 없습니다(시드 데이터 누락): " + request.category()));

        String promptText = template.getContent().replace("{input}", request.inputText())
                + "\n\n" + outputConverter.getFormat();

        long startedAt = System.currentTimeMillis();
        try {
            ChatResponse chatResponse = chatClientBuilder.build()
                    .prompt()
                    .user(promptText)
                    .options(buildChatOptions())
                    .call()
                    .chatResponse();
            long latencyMs = System.currentTimeMillis() - startedAt;

            String rawContent = chatResponse.getResult().getOutput().getText();
            ProductAiSuggestion suggestion = outputConverter.convert(rawContent);

            var usage = chatResponse.getMetadata().getUsage();
            aiSuggestionLogRecorder.recordSuccess(seller, request.category(), request.inputText(),
                    suggestion.suggestedName(), suggestion.suggestedDescription(),
                    suggestion.suggestedBasePrice(), suggestion.suggestedMaxParticipants(),
                    usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(), latencyMs);

            log.info("AI 상품등록 제안 성공: sellerId={}, category={}, totalTokens={}, latencyMs={}",
                    seller.getId(), request.category(), usage.getTotalTokens(), latencyMs);
            return suggestion;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startedAt;
            log.error("AI 상품등록 제안 실패: sellerId={}, category={}, latencyMs={}",
                    seller.getId(), request.category(), latencyMs, e);
            aiSuggestionLogRecorder.recordFailure(
                    seller, request.category(), request.inputText(), e.getMessage(), latencyMs);
            throw new BusinessException(ErrorCode.AI_SUGGESTION_FAILED);
        }
    }

    // temperature: 구조화된 JSON을 일관되게 뽑는 게 목적이라 창의성보다 일관성을 우선해 낮게 설정.
    // max_tokens: 상품명·설명·가격 몇 필드짜리 짧은 JSON 응답이면 충분(실측 후 근거를 design.md에 기록).
    private OpenAiChatOptions.Builder buildChatOptions() {
        return OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.3)
                .maxTokens(500);
    }

    private void requireSeller(MemberUserDetails principal) {
        if (principal.getMember().getRole() != Role.SELLER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
