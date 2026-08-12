package com.gong9ri.gong9ri.common.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그인 시도 제한 2단계 — 계정(username) 단위 실패 횟수 제한. {@code common/filter/RateLimitFilter}의
 * IP 단위 제어는 "여러 IP를 돌려가며 특정 계정 하나만 노리는" 공격은 못 막아서 별도로 둔다.
 *
 * <p>존재하지 않는 username도 실제 계정과 똑같이 카운트한다 — 그래야 "이 계정만 잠금 메시지가 뜬다"는
 * 걸로 계정 존재 여부가 새지 않는다(기존 {@code LOGIN_FAILED} 통일 응답과 같은 원칙).
 *
 * <p><b>알려진 트레이드오프</b>: 공격자가 피해자 계정 이름만 알면 일부러 틀린 비밀번호를 반복 입력해서
 * 그 계정의 정상 로그인을 막아버릴 수 있다(계정 잠금 DoS). CAPTCHA·점진적 백오프 같은 정교한 방어는
 * 이번 스코프 밖 — docs/dev/auth/login/design.md 참고.
 *
 * <p>임계값(10분·5회)은 실측 근거 없는 초기값이다. fail-open: Redis 장애 시 잠금 없이 통과시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final long LIMIT = 5;

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String username) {
        try {
            String value = redisTemplate.opsForValue().get(key(username));
            // recordFailure()로 이미 LIMIT번 실패가 기록된 상태라면(>=), 그다음 시도부터는
            // authenticate() 자체를 안 태우고 미리 막는다 — "LIMIT번 틀리면 잠긴다"는 표현과
            // 정확히 맞아떨어지게 하려고 초과(>)가 아니라 도달(>=)로 판정한다.
            return value != null && Long.parseLong(value) >= LIMIT;
        } catch (Exception e) {
            log.warn("로그인 시도 제한용 Redis 호출 실패, 잠금 없이 통과: {}", e.getMessage());
            return false;
        }
    }

    public void recordFailure(String username) {
        try {
            String key = key(username);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
        } catch (Exception e) {
            log.warn("로그인 시도 제한용 Redis 호출 실패, 실패 기록 안 됨: {}", e.getMessage());
        }
    }

    public void recordSuccess(String username) {
        try {
            redisTemplate.delete(key(username));
        } catch (Exception e) {
            log.warn("로그인 시도 제한용 Redis 호출 실패, 카운터 리셋 안 됨: {}", e.getMessage());
        }
    }

    private String key(String username) {
        return "login-fail:" + username;
    }
}
