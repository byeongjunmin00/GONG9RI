package com.gong9ri.gong9ri.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 로그인 시도 제한 2단계(계정 단위) — 실제 Redis 대상으로 잠금/실패기록/성공리셋을 검증한다.
 * Redis는 JPA 트랜잭션 롤백 범위 밖이라 {@code @BeforeEach}/{@code @AfterEach}에서 직접 정리한다
 * (RateLimitFilterTest와 동일한 관례).
 */
@SpringBootTest
class LoginAttemptGuardTest {

    private static final String TEST_USERNAME = "login-guard-test-user";
    private static final String KEY = "login-fail:" + TEST_USERNAME;
    private static final int LIMIT = 5;

    @Autowired
    private LoginAttemptGuard loginAttemptGuard;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUpBefore() {
        redisTemplate.delete(KEY);
    }

    @AfterEach
    void cleanUpAfter() {
        redisTemplate.delete(KEY);
    }

    @Test
    @DisplayName("임계값(5회) 미만 실패는 잠기지 않는다")
    void recordFailure_belowLimit_notLocked() {
        for (int i = 0; i < LIMIT - 1; i++) {
            loginAttemptGuard.recordFailure(TEST_USERNAME);
        }

        assertFalse(loginAttemptGuard.isLocked(TEST_USERNAME));
    }

    @Test
    @DisplayName("임계값(5회)에 도달하면 잠긴다")
    void recordFailure_reachesLimit_locked() {
        for (int i = 0; i < LIMIT; i++) {
            loginAttemptGuard.recordFailure(TEST_USERNAME);
        }

        assertTrue(loginAttemptGuard.isLocked(TEST_USERNAME));
    }

    @Test
    @DisplayName("성공 기록 후에는 실패 횟수가 리셋되어 다시 잠기지 않는다")
    void recordSuccess_resetsFailureCount() {
        for (int i = 0; i < LIMIT; i++) {
            loginAttemptGuard.recordFailure(TEST_USERNAME);
        }
        assertTrue(loginAttemptGuard.isLocked(TEST_USERNAME));

        loginAttemptGuard.recordSuccess(TEST_USERNAME);

        assertFalse(loginAttemptGuard.isLocked(TEST_USERNAME));
    }
}
