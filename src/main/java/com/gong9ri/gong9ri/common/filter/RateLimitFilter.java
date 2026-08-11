package com.gong9ri.gong9ri.common.filter;

import com.gong9ri.gong9ri.common.exception.ErrorCode;
import com.gong9ri.gong9ri.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
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
 * {@code POST /api/teams/{teamId}/join} 클라이언트(IP) 단위 요청 제어(발제 백엔드 도전과제 "트래픽 제어").
 * k6 스파이크 테스트(docs/logs/team/crud/004-spike-test.md)로 이 엔드포인트가 VU 2970~3000 사이에서
 * 실제로 무너지는 걸 확인해뒀다 — "같은 클라이언트가 반복 요청하는" 훨씬 흔한 시나리오를 여기서 막는다.
 * 임계값(10초당 20회)은 실측 근거 없는 초기값이다(design.md 참고) — 정상 사용자 클릭 패턴은 절대
 * 도달 못 하지만, 스크립트성 반복 요청은 확실히 걸러내는 수준으로 잡았다.
 *
 * <p>{@code RequestLoggingFilter}(traceId 발급)보다 뒤에서 실행되도록 order를 잡아서, 429로 막힌
 * 요청도 traceId·액세스 로그 범위에 그대로 포함되게 한다.
 *
 * <p><b>fail-open</b>: Redis 호출 자체가 실패하면(장애) 요청을 막지 않고 그냥 통과시킨다 — rate limit이
 * 잠깐 안 걸리는 것보다 Redis 장애가 핵심 기능(공구 참가)까지 막아버리는 게 훨씬 나쁘다(AI 기능의
 * 장애격리 원칙과 같은 판단).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern TEAM_JOIN_PATTERN = Pattern.compile("^/api/teams/\\d+/join$");
    private static final Duration WINDOW = Duration.ofSeconds(10);
    private static final long LIMIT = 20;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isRateLimited(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = resolveClientId(request);
        if (exceedsLimit(clientId)) {
            log.warn("트래픽 제어 — 요청 거절: clientId={}, uri={}", clientId, request.getRequestURI());
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && TEAM_JOIN_PATTERN.matcher(request.getRequestURI()).matches();
    }

    private boolean exceedsLimit(String clientId) {
        String key = "rate-limit:team-join:" + clientId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
            return count != null && count > LIMIT;
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
