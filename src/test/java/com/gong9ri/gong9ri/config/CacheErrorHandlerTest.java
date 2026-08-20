package com.gong9ri.gong9ri.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

/**
 * 캐시(Redis) 장애 시 요청을 막지 않고 원본에서 읽게 하는지 고정한다.
 *
 * <p><b>왜 필요했나</b> — 이 핸들러가 없으면 기본 동작({@code SimpleCacheErrorHandler})이 Redis 예외를
 * 그대로 다시 던져, {@code @Cacheable}이 붙은 상품 목록·상세가 <b>500</b>이 된다. Redis를 끄고 실제로
 * 띄워 확인했다(2026-08-21: 적용 전 {@code GET /api/products} → 500, 적용 후 → 200 + 정상 데이터).
 *
 * <p><b>함정</b>: {@code CacheErrorHandler} 빈을 선언하는 것만으로는 적용되지 않는다. 스프링은
 * {@code CachingConfigurer}를 구현한 설정 클래스의 {@code errorHandler()}만 본다 — 빈만 만들어두고
 * 여전히 500이 나는 걸 실측으로 확인한 뒤에야 알았다. 그래서 이 테스트는 "빈이 존재하는지"가 아니라
 * <b>컨테이너가 실제로 그 핸들러를 쓰도록 배선됐는지</b>를 본다.
 */
@SpringBootTest
class CacheErrorHandlerTest {

    /** {@code CachingConfigurer.errorHandler()}로 배선된 핸들러가 주입된다. */
    @Autowired
    private CacheErrorHandler cacheErrorHandler;

    @Autowired
    private CacheConfig cacheConfig;

    @Test
    @DisplayName("캐시 설정이 CachingConfigurer로 배선돼 있다 — 빈만 선언하면 적용되지 않는다")
    void cacheConfig_isWiredAsCachingConfigurer() {
        assertNotNull(cacheErrorHandler, "CacheErrorHandler가 컨텍스트에 있어야 한다");
        org.junit.jupiter.api.Assertions.assertInstanceOf(
                org.springframework.cache.annotation.CachingConfigurer.class, cacheConfig,
                "CacheConfig가 CachingConfigurer를 구현해야 스프링이 errorHandler()를 사용한다");
    }

    @Test
    @DisplayName("Redis 연결 실패는 삼키고 요청을 계속 진행시킨다 (fail-open)")
    void redisFailure_isSwallowed() {
        Cache cache = new org.springframework.cache.concurrent.ConcurrentMapCache("productList");
        RedisConnectionFailureException failure = new RedisConnectionFailureException("Unable to connect to Redis");

        assertDoesNotThrow(() -> cacheErrorHandler.handleCacheGetError(failure, cache, "0-20"),
                "조회 실패가 그대로 올라가면 상품 목록이 500이 된다");
        assertDoesNotThrow(() -> cacheErrorHandler.handleCachePutError(failure, cache, "0-20", "value"));
        assertDoesNotThrow(() -> cacheErrorHandler.handleCacheEvictError(failure, cache, "0-20"));
        assertDoesNotThrow(() -> cacheErrorHandler.handleCacheClearError(failure, cache));
    }

    @Test
    @DisplayName("연결 실패가 아닌 캐시 예외(타임아웃 등)도 동일하게 삼킨다")
    void otherCacheFailure_isAlsoSwallowed() {
        // Redis가 살아있어도 느려서 타임아웃이 날 수 있다. 그때도 사이트가 죽으면 안 된다.
        Cache cache = new org.springframework.cache.concurrent.ConcurrentMapCache("productDetail");
        QueryTimeoutException timeout = new QueryTimeoutException("Redis command timed out");

        assertDoesNotThrow(() -> cacheErrorHandler.handleCacheGetError(timeout, cache, 1L));
        assertDoesNotThrow(() -> cacheErrorHandler.handleCacheEvictError(timeout, cache, 1L));
    }
}
