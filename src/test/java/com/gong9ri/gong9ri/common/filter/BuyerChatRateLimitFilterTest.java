package com.gong9ri.gong9ri.common.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 구매자 챗봇(SSE) 트래픽 제어(Redis 고정 윈도우 rate limit) 검증(2026-08-19 추가,
 * docs/dev/ai/buyer-chatbot/design.md "비용인식 — 요청 제한" 참고).
 *
 * <p>{@code RateLimitFilter}는 {@code @Order(HIGHEST_PRECEDENCE + 10)}로 Spring Security 필터체인보다도
 * 먼저 실행되므로, 비로그인 요청으로도 이 필터의 매칭·카운팅 동작만 독립적으로 검증할 수 있다 — 로그인
 * 안 한 요청은 필터를 통과해도 그 다음 Security 단계에서 401로 막히니(챗봇 실제 응답 로직까지 갈 필요
 * 없음), SSE 컨트롤러의 비동기 완료를 기다리거나 {@code ChatClient}를 목으로 대체할 필요가 없다 —
 * 임계값 이내는 401(필터 통과 후 인증 단계에서 거절), 임계값 초과는 429(필터 자체에서 거절)로 구분된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BuyerChatRateLimitFilterTest {

    private static final String TEST_CLIENT_IP = "203.0.113.88";
    private static final String RATE_LIMIT_KEY = "rate-limit:buyer-chat:" + TEST_CLIENT_IP;
    private static final int LIMIT = 10;

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
    @DisplayName("임계값(1분 10회) 이내 요청은 rate limit에 안 걸리고 필터를 통과한다(그 다음 인증 단계에서 401)")
    void withinLimit_passesFilter() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(post("/api/buyer/chat/messages")
                            .header("X-Forwarded-For", TEST_CLIENT_IP)
                            .contentType("application/json")
                            .content("{\"content\":\"안녕\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("같은 클라이언트가 임계값(1분 10회)을 넘겨 요청하면 11번째부터 429 TOO_MANY_REQUESTS")
    void exceedsLimit_returns429() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(post("/api/buyer/chat/messages")
                            .header("X-Forwarded-For", TEST_CLIENT_IP)
                            .contentType("application/json")
                            .content("{\"content\":\"안녕\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/buyer/chat/messages")
                        .header("X-Forwarded-For", TEST_CLIENT_IP)
                        .contentType("application/json")
                        .content("{\"content\":\"안녕\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("챗봇이 아닌 다른 엔드포인트는 이 rate limit 대상이 아니다")
    void otherEndpoint_notRateLimited() throws Exception {
        for (int i = 0; i < LIMIT + 5; i++) {
            mockMvc.perform(get("/api/buyer/chat/sessions/1/messages").header("X-Forwarded-For", TEST_CLIENT_IP))
                    .andExpect(status().isUnauthorized());
        }
    }
}
