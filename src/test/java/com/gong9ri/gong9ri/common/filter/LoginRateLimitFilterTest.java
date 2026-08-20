package com.gong9ri.gong9ri.common.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 로그인 시도 제한 1단계(IP 단위) — {@code RateLimitFilter}에 로그인 규칙(윈도우 60초·임계값 10회)이
 * 실제로 붙어 동작하는지 검증한다. 매 요청마다 존재하지 않는 계정을 다르게 써서, 이 테스트가 2단계
 * (계정 단위 잠금, {@code LoginAttemptGuard})와 서로 간섭하지 않게 한다 — 여기서 보고 싶은 건 순수하게
 * "같은 클라이언트(IP)"가 임계값을 넘기는지뿐이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginRateLimitFilterTest {

    private static final String TEST_CLIENT_IP = "203.0.113.150";
    private static final String RATE_LIMIT_KEY = "rate-limit:login:" + TEST_CLIENT_IP;
    private static final int LIMIT = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUpBefore() {
        clearRedisState();
    }

    @AfterEach
    void cleanUpAfter() {
        clearRedisState();
    }

    /**
     * rate limit 카운터뿐 아니라 <b>계정 잠금 기록(login-fail:*)도 지운다.</b>
     *
     * <p>이 테스트는 같은 아이디로 로그인 실패를 반복하는데, {@code LoginAttemptGuard}가 그 실패를
     * 10분 창으로 누적해 계정을 잠근다. rate limit 키만 지우면 잠금 기록이 남아, 테스트를 짧은 시간에
     * 여러 번 돌리면 <b>필터가 아니라 컨트롤러가 429를 던져</b> 401을 기대하는 단언이 깨진다
     * (2026-08-21 실제로 겪음 — 단독 실행에서도 재현됐다).
     *
     * <p>테스트가 남긴 상태를 스스로 치우지 않으면 "언제 마지막으로 돌렸는지"에 결과가 달라진다.
     */
    private void clearRedisState() {
        redisTemplate.delete(RATE_LIMIT_KEY);
        for (int i = 0; i <= LIMIT; i++) {
            redisTemplate.delete("login-fail:login-rl-nonexistent-" + i);
        }
    }

    private String loginBody(int i) throws Exception {
        Map<String, Object> request = Map.of("username", "login-rl-nonexistent-" + i, "password", "wrong-password");
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("임계값(10회) 이내 요청은 rate limit에 걸리지 않고 기존 로직(LOGIN_FAILED) 그대로 동작한다")
    void withinLimit_notBlocked() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", TEST_CLIENT_IP)
                            .contentType("application/json")
                            .content(loginBody(i)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
    }

    @Test
    @DisplayName("같은 클라이언트가 임계값(10회)을 넘겨 요청하면 11번째부터 429 TOO_MANY_REQUESTS")
    void exceedsLimit_returns429() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", TEST_CLIENT_IP)
                            .contentType("application/json")
                            .content(loginBody(i)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", TEST_CLIENT_IP)
                        .contentType("application/json")
                        .content(loginBody(LIMIT)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }
}
