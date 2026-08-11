package com.gong9ri.gong9ri.common.filter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gong9ri.gong9ri.common.security.MemberUserDetails;
import com.gong9ri.gong9ri.entity.Member;
import com.gong9ri.gong9ri.entity.Role;
import com.gong9ri.gong9ri.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * team/join 트래픽 제어(Redis 고정 윈도우 rate limit) 검증.
 * 기존 {@code TeamControllerTest}도 같은 team/join 엔드포인트를 반복 호출하므로, 카운터가 섞이지
 * 않도록 이 테스트 전용 X-Forwarded-For 값을 고정해서 쓰고 매 테스트 전후로 그 키만 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RateLimitFilterTest {

    private static final String TEST_CLIENT_IP = "203.0.113.77";
    private static final String RATE_LIMIT_KEY = "rate-limit:team-join:" + TEST_CLIENT_IP;
    private static final int LIMIT = 20;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void cleanUpBefore() {
        redisTemplate.delete(RATE_LIMIT_KEY);
    }

    @AfterEach
    void cleanUpAfter() {
        redisTemplate.delete(RATE_LIMIT_KEY);
    }

    private RequestPostProcessor asUser(Member member) {
        MemberUserDetails principal = new MemberUserDetails(member);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(token);
    }

    @Test
    @DisplayName("임계값(20회) 이내 요청은 rate limit에 걸리지 않고 기존 로직 그대로 동작한다")
    void withinLimit_notBlocked() throws Exception {
        Member joiner = memberRepository.save(
                new Member("rateLimitJoiner1", "encoded-password", "테스트유저", "rateLimitJoiner1@test.com", Role.BUYER));

        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(post("/api/teams/999999/join")
                            .header("X-Forwarded-For", TEST_CLIENT_IP)
                            .with(asUser(joiner)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("같은 클라이언트가 임계값(20회)을 넘겨 요청하면 21번째부터 429 TOO_MANY_REQUESTS")
    void exceedsLimit_returns429() throws Exception {
        Member joiner = memberRepository.save(
                new Member("rateLimitJoiner2", "encoded-password", "테스트유저", "rateLimitJoiner2@test.com", Role.BUYER));

        for (int i = 0; i < LIMIT; i++) {
            mockMvc.perform(post("/api/teams/999999/join")
                            .header("X-Forwarded-For", TEST_CLIENT_IP)
                            .with(asUser(joiner)))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/teams/999999/join")
                        .header("X-Forwarded-For", TEST_CLIENT_IP)
                        .with(asUser(joiner)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("team/join이 아닌 다른 엔드포인트는 rate limit 대상이 아니다")
    void otherEndpoint_notRateLimited() throws Exception {
        for (int i = 0; i < LIMIT + 5; i++) {
            mockMvc.perform(get("/api/products/999999/teams").header("X-Forwarded-For", TEST_CLIENT_IP))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        }
    }
}
