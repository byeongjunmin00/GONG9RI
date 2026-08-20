package com.gong9ri.gong9ri.common.filter;

import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 클라이언트(IP) 단위 요청 제어. 원래 {@code POST /api/teams/{teamId}/join} 하나만(발제 백엔드
 * 도전과제 "트래픽 제어" — k6 스파이크 테스트로 VU 2970~3000 사이에서 실제로 무너지는 걸 확인해뒀다,
 * docs/logs/team/crud/004-spike-test.md) 처리하다가, 로그인 시도 제한(브루트포스 방어 1단계)을
 * 추가하면서 규칙을 리스트로 일반화했다 — 메커니즘(INCR+EXPIRE 고정 윈도우, fail-open, 429 수동
 * 직렬화)이 완전히 같은 걸 두 번째로 실제 쓰는 시점이라, 거의 동일한 필터 클래스를 하나 더 만드는
 * 것보다 매칭 규칙만 뽑아내는 쪽이 중복이 훨씬 적다.
 *
 * <p>각 엔드포인트의 임계값은 전부 실측 근거 없는 초기값이다(각 기능 design.md 참고) — 정상 사용자
 * 패턴은 절대 도달 못 하지만 스크립트성 반복 요청은 확실히 걸러내는 수준으로 감으로 잡았다.
 *
 * <p>{@code RequestLoggingFilter}(traceId 발급)보다 뒤에서 실행되도록 order를 잡아서, 429로 막힌
 * 요청도 traceId·액세스 로그 범위에 그대로 포함되게 한다.
 *
 * <p><b>fail-open</b>: Redis 호출 자체가 실패하면(장애) 요청을 막지 않고 그냥 통과시킨다 — rate limit이
 * 잠깐 안 걸리는 것보다 Redis 장애가 핵심 기능까지 막아버리는 게 훨씬 나쁘다(AI 기능의 장애격리
 * 원칙과 같은 판단).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private record RateLimitRule(String method, Pattern pathPattern, String keyPrefix, Duration window, long limit) {

        boolean matches(HttpServletRequest request) {
            return method.equalsIgnoreCase(request.getMethod()) && pathPattern.matcher(request.getRequestURI()).matches();
        }
    }

    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("POST", Pattern.compile("^/api/teams/\\d+/join$"), "team-join", Duration.ofSeconds(10), 20),
            new RateLimitRule("POST", Pattern.compile("^/api/auth/login$"), "login", Duration.ofSeconds(60), 10),
            // 로그인 고도화 2단계 — 이메일 폭탄(무차별 재발송/재설정 요청) 방지.
            new RateLimitRule("POST", Pattern.compile("^/api/auth/verify-email/resend$"), "verify-email-resend", Duration.ofMinutes(5), 3),
            new RateLimitRule("POST", Pattern.compile("^/api/auth/password/reset-request$"), "password-reset-request", Duration.ofMinutes(5), 3),
            // 구매자 챗봇(SSE) — 로그인(구매자) 게이트 하나만 방어선이었다(비용 인식 원칙, docs/dev/ai/
            // buyer-chatbot/design.md). 호출 한 번마다 OpenAI 비용이 실제로 발생하는데 이 엔드포인트만
            // 트래픽 제어 규칙이 없었던 걸 뒤늦게 발견해서(2026-08-19) 추가한다. 정상적인 채팅 사용 패턴
            // (한 번에 메시지 하나씩, 답변 기다렸다 다음 질문)은 1분에 10회면 절대 못 채우지만, 스크립트로
            // 반복 호출하는 건 확실히 막는다.
            new RateLimitRule("POST", Pattern.compile("^/api/buyer/chat/messages$"), "buyer-chat", Duration.ofMinutes(1), 10),
            // 판매자 상품등록 도우미 — 챗봇과 함께 OpenAI를 호출하는 나머지 한 곳이다. 위 챗봇 규칙을
            // 추가할 때 "여기도 같은 갭"이라고 확인해놓고 그때 함께 처리하지 않아, 한동안 AI 호출 경로
            // 둘 중 하나만 막혀 있었다(2026-08-20 마무리). 챗봇보다 한도를 낮게(1분/5회) 잡은 이유 —
            // 이건 상품 하나를 등록하기 전에 초안을 받아보는 용도라 연속 호출할 일이 챗봇보다 적고,
            // 한 번의 호출이 더 긴 프롬프트(카테고리별 템플릿 + 사용자 입력)를 태워 단가가 높다.
            new RateLimitRule("POST", Pattern.compile("^/api/seller/products/ai-suggest$"), "ai-suggest",
                    Duration.ofMinutes(1), 5));

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<RateLimitRule> rule = RULES.stream().filter(r -> r.matches(request)).findFirst();
        if (rule.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = resolveClientId(request);
        if (exceedsLimit(rule.get(), clientId)) {
            log.warn("트래픽 제어 — 요청 거절: rule={}, clientId={}, uri={}", rule.get().keyPrefix(), clientId, request.getRequestURI());
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean exceedsLimit(RateLimitRule rule, String clientId) {
        String key = "rate-limit:" + rule.keyPrefix() + ":" + clientId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, rule.window());
            }
            return count != null && count > rule.limit();
        } catch (Exception e) {
            // fail-open: Redis 장애 시 rate limit 없이 통과시킨다.
            log.warn("트래픽 제어용 Redis 호출 실패, rate limit 없이 통과: {}", e.getMessage());
            return false;
        }
    }

    // Railway 등 프록시 뒤에서는 request.getRemoteAddr()가 전부 프록시 IP로 잡히므로
    // X-Forwarded-For(첫 번째 값 = 실제 클라이언트)를 우선 사용한다.
    private String resolveClientId(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.TOO_MANY_REQUESTS.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.failure(
                ErrorCode.TOO_MANY_REQUESTS.name(), ErrorCode.TOO_MANY_REQUESTS.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
