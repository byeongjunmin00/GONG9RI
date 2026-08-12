package com.gong9ri.gong9ri.common.security;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증 토큰과 비밀번호 재설정 토큰을 둘 다 처리하는 공용 컴포넌트 — 같은 메커니즘(랜덤 토큰 발급,
 * Redis TTL로 만료, 1회 사용 후 즉시 삭제)을 두 번째로 실제 쓰는 시점이라 {@code RateLimitFilter}를
 * 규칙 리스트로 일반화했을 때와 같은 판단으로, 거의 동일한 클래스 두 개 대신 이거 하나로 처리한다.
 *
 * <p>키 형식 {@code {prefix}:{token}} → 값 {@code memberId}. {@code prefix}로 용도를 구분한다
 * (예: {@code "email-verify"}, {@code "password-reset"}).
 *
 * <p>{@link UUID#randomUUID()}는 내부적으로 {@link java.security.SecureRandom} 기반이라 토큰
 * 생성에 그대로 써도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate redisTemplate;

    public String issue(String prefix, Long memberId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(prefix, token), String.valueOf(memberId), ttl);
        return token;
    }

    /**
     * 토큰을 조회하고 즉시 삭제한다(1회성 보장). 만료됐든 이미 썼든 존재하지 않든 전부 빈 Optional로
     * 통일해서, 호출자가 그 차이를 구분해서 클라이언트에 알려주지 않게 한다(계정 존재 여부 등 정보가
     * 새는 걸 막는 이 프로젝트의 기존 원칙과 동일).
     *
     * <p>다른 Redis 기반 컴포넌트(RateLimitFilter/LoginAttemptGuard)와 달리 여기는 Redis 장애 시
     * fail-open이 아니라 fail-closed(= 무효 토큰 취급)다 — 토큰의 진위를 판단할 근거 자체가 Redis뿐이라,
     * "장애 시 통과"를 허용하면 아무 문자열이나 유효한 토큰으로 받아들이는 보안 구멍이 된다.
     */
    public Optional<Long> resolveAndConsume(String prefix, String token) {
        try {
            String key = key(prefix, token);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            redisTemplate.delete(key);
            return Optional.of(Long.parseLong(value));
        } catch (Exception e) {
            log.warn("토큰 조회/소진 실패: prefix={}, error={}", prefix, e.getMessage());
            return Optional.empty();
        }
    }

    private String key(String prefix, String token) {
        return prefix + ":" + token;
    }
}
