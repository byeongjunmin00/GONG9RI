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
            new RateLimitRule("POST", Pattern.compile("^/api/auth/login$"), "login", Duration.ofSeconds(60), 10));

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
