package com.gong9ri.gong9ri.common.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 판매자 상품등록 도우미(AI) 트래픽 제어 검증 — OpenAI를 호출하는 두 엔드포인트 중 챗봇에만 규칙이
 * 있고 이쪽은 빠져 있던 갭을 메운다(2026-08-20, docs/dev/ai/product-suggestion/design.md).
 *
 * <p>{@code BuyerChatRateLimitFilterTest}와 동일한 방식으로 <b>비로그인 요청</b>을 쓴다 —
 * {@code RateLimitFilter}가 Spring Security 필터체인보다 먼저 실행되므로, 임계값 이내는 401
 * (필터를 통과한 뒤 인증 단계에서 거절), 초과는 429(필터 자체에서 거절)로 갈린다. 덕분에 실제 OpenAI
 * 호출이나 {@code ChatClient} 목 없이 필터의 매칭·카운팅만 독립적으로 검증할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiSuggestRateLimitFilterTest {

    private static final String TEST_CLIENT_IP = "203.0.113.99";
    private static final String RATE_LIMIT_KEY = "rate-limit:ai-suggest:" + TEST_CLIENT_IP;
    private static final int LIMIT = 5;

    private static final String REQUEST_BODY = "{\"prompt\":\"제주 감귤 5kg 만원쯤에 팔고 싶어요\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUpBefore() {
        redisTemplate.delete(RATE_LIMIT_KEY);
    }

    @AfterEach
    void cleanUpAfter() {
        redisTemplate.delete(RATE_LIMIT_KEY);
    }

    @Test
    @DisplayName("임계값(1분 5회) 이내 요청은 rate limit에 안 걸리고 필터를 통과한다(그 다음 인증 단계에서 401)")
    void withinLimit_passesFilter() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(post("/api/seller/products/ai-suggest")
                            .header("X-Forwarded-For", TEST_CLIENT_IP)
                            .contentType("application/json")
                            .content(REQUEST_BODY))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("같은 클라이언트가 임계값(1분 5회)을 넘겨 요청하면 6번째부터 429 TOO_MANY_REQUESTS")
    void exceedsLimit_returns429() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(post("/api/seller/products/ai-suggest")
                            .header("X-Forwarded-For", TEST_CLIENT_IP)
                            .contentType("application/json")
                            .content(REQUEST_BODY))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/seller/products/ai-suggest")
                        .header("X-Forwarded-For", TEST_CLIENT_IP)
                        .contentType("application/json")
                        .content(REQUEST_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("상품 등록(POST /api/seller/products)은 AI 호출이 아니라 이 rate limit 대상이 아니다")
    void productRegister_notRateLimited() throws Exception {
        // 경로가 /api/seller/products로 시작해 접두사만 보면 헷갈리기 쉬운 이웃 엔드포인트다 —
        // 정규식이 ai-suggest만 정확히 매칭하는지(과잉 차단이 없는지) 함께 고정한다.
        for (int i = 0; i < LIMIT + 3; i++) {
            mockMvc.perform(post("/api/seller/products")
                            .header("X-Forwarded-For", TEST_CLIENT_IP)
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
