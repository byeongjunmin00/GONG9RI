package com.gong9ri.gong9ri.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.dto.RevenueResponse;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * CacheConfig가 실제로 JSON 값 직렬화기를 사용하도록 구성하는지 검증한다
 * (docs/logs/mypage/view/002-caching.md Attempt 1에서 확정된 결함 재발 방지).
 *
 * 기본 Redis 캐시 직렬화기(JdkSerializationRedisSerializer)는 캐시 값이 Serializable일 것을
 * 요구하는데, RevenueResponse는 record이며 Serializable을 구현하지 않는다.
 * 실제 Redis 서버 연결 없이(LettuceConnectionFactory는 생성 시점에 연결을 맺지 않는다)
 * RedisCacheConfiguration에 설정된 값 직렬화기로 non-serializable record를 직렬화/역직렬화해
 * 왕복이 성공하는지로 검증한다.
 */
class CacheConfigTest {

    @Test
    @DisplayName("sellerRevenue 캐시는 non-serializable record도 직렬화/역직렬화할 수 있는 JSON 직렬화기를 사용한다")
    void sellerRevenueCache_serializesNonSerializableRecordAsJson() {
        CacheConfig cacheConfig = new CacheConfig();
        RedisCacheManager.RedisCacheManagerBuilder builder =
                RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(new LettuceConnectionFactory());

        cacheConfig.sellerRevenueCacheCustomizer().customize(builder);

        Optional<RedisCacheConfiguration> sellerRevenueConfig =
                builder.getCacheConfigurationFor(CacheConfig.SELLER_REVENUE_CACHE);
        assertTrue(sellerRevenueConfig.isPresent());

        RedisSerializationContext.SerializationPair<Object> valueSerializer =
                sellerRevenueConfig.get().getValueSerializationPair();

        RevenueResponse original = new RevenueResponse(10000, 3L, 1L);

        ByteBuffer serialized = valueSerializer.write(original);
        Object deserialized = valueSerializer.read(serialized);

        assertEquals(original, deserialized);
    }
}
