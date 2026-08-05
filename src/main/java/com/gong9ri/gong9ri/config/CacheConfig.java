package com.gong9ri.gong9ri.config;

import com.gong9ri.gong9ri.dto.RevenueResponse;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * 캐싱 설정 (docs/policy/caching.md).
 * 무효화 트리거(payment/create, team/deadline-check)가 누락되더라도 캐시가 옛 값을 영구히 반환하지 않도록
 * TTL을 안전장치로 둔다 — 무효화가 항상 성공한다고 전제하지 않는다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 판매자 수익 현황(mypage/seller-revenue) 캐시 이름. */
    public static final String SELLER_REVENUE_CACHE = "sellerRevenue";

    private static final Duration SELLER_REVENUE_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisCacheManagerBuilderCustomizer sellerRevenueCacheCustomizer() {
        // 값 직렬화기를 JSON(JacksonJsonRedisSerializer)으로 명시한다.
        // 기본값(JdkSerializationRedisSerializer)은 캐시 대상 DTO가 Serializable일 것을 요구하는데,
        // 이 프로젝트의 REST 응답 DTO는 JSON 기반이라 Serializable을 구현하지 않는다.
        // 이 캐시는 RevenueResponse 하나만 값으로 다루므로, 타입을 고정한 직렬화기를 쓴다 —
        // 타입 정보를 값에 함께 저장하지 않는 범용 직렬화기(예: GenericJacksonJsonRedisSerializer)를 쓰면,
        // Spring Cache 추상화가 조회 시 목표 타입을 넘기지 않아 역직렬화 결과가 RevenueResponse가 아닌
        // 일반 Map으로 반환되어 캐시 히트 시 ClassCastException이 발생한다.
        RedisCacheConfiguration sellerRevenueConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(SELLER_REVENUE_TTL)
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(RevenueResponse.class)));
        return builder -> builder.withCacheConfiguration(SELLER_REVENUE_CACHE, sellerRevenueConfig);
    }
}
