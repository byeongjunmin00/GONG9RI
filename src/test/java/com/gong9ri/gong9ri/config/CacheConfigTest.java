package com.gong9ri.gong9ri.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gong9ri.gong9ri.dto.PriceTierResponse;
import com.gong9ri.gong9ri.dto.ProductPageResponse;
import com.gong9ri.gong9ri.dto.ProductResponse;
import com.gong9ri.gong9ri.dto.ProductSummaryResponse;
import com.gong9ri.gong9ri.entity.ProductCategory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
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
 * 요구하는데, RevenueResponse/ProductPageResponse/ProductResponse는 모두 record이며
 * Serializable을 구현하지 않는다.
 * 실제 Redis 서버 연결 없이(LettuceConnectionFactory는 생성 시점에 연결을 맺지 않는다)
 * RedisCacheConfiguration에 설정된 값 직렬화기로 non-serializable record를 직렬화/역직렬화해
 * 왕복이 성공하는지로 검증한다.
 */
class CacheConfigTest {

    @Test
    @DisplayName("productList 캐시는 non-serializable record(ProductPageResponse, 중첩 ProductSummaryResponse 포함)도 왕복 직렬화한다")
    void productListCache_serializesNonSerializableRecordAsJson() {
        CacheConfig cacheConfig = new CacheConfig();
        RedisCacheManager.RedisCacheManagerBuilder builder =
                RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(new LettuceConnectionFactory());

        cacheConfig.productListCacheCustomizer().customize(builder);

        Optional<RedisCacheConfiguration> productListConfig =
                builder.getCacheConfigurationFor(CacheConfig.PRODUCT_LIST_CACHE);
        assertTrue(productListConfig.isPresent());

        RedisSerializationContext.SerializationPair<Object> valueSerializer =
                productListConfig.get().getValueSerializationPair();

        ProductSummaryResponse summary = new ProductSummaryResponse(
                1L, "제주 감귤 5kg", 25000, 15000, 10, "테스트판매자", LocalDateTime.now(), null, ProductCategory.FOOD,
                null, null, null, null, false, null, null);
        ProductPageResponse original = new ProductPageResponse(List.of(summary), 0, 20, 1L);

        ByteBuffer serialized = valueSerializer.write(original);
        Object deserialized = valueSerializer.read(serialized);

        assertEquals(original, deserialized);
    }

    @Test
    @DisplayName("sellerTrustedBadge 필드가 없는(배포 전에 캐시된) 옛 JSON도 예외 없이 역직렬화된다 — "
            + "실제 프로덕션에서 primitive boolean 때문에 발생했던 500 재발 방지")
    void productListCache_deserializesOldJsonMissingNewField_withoutThrowing() {
        CacheConfig cacheConfig = new CacheConfig();
        RedisCacheManager.RedisCacheManagerBuilder builder =
                RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(new LettuceConnectionFactory());
        cacheConfig.productListCacheCustomizer().customize(builder);
        RedisSerializationContext.SerializationPair<Object> valueSerializer = builder
                .getCacheConfigurationFor(CacheConfig.PRODUCT_LIST_CACHE).get().getValueSerializationPair();

        // sellerTrustedBadge 필드를 통째로 뺀 JSON — product/seller-trust 배포 이전에 이미 캐시돼 있던
        // 항목을 그대로 흉내낸다. 필드가 boolean(primitive)이었을 때는 이 상황에서
        // MismatchedInputException("Cannot map `null` into type `boolean`")이 터져 GET /api/products가
        // 500을 냈다(2026-08-18 실제 프로덕션 로그로 확인).
        String oldJson = "{\"content\":[{"
                + "\"productId\":1,\"name\":\"제주 감귤 5kg\",\"basePrice\":25000,\"bestPrice\":15000,"
                + "\"maxParticipants\":10,\"sellerName\":\"테스트판매자\",\"createdAt\":\"2026-08-10T05:53:47.456061\","
                + "\"imageUrl\":null,\"category\":\"FOOD\",\"activeTeamCurrentCount\":null,"
                + "\"activeTeamTargetParticipants\":null,\"activeTeamDeadline\":null,\"openAt\":null"
                + "}],\"page\":0,\"size\":20,\"totalElements\":1}";
        ByteBuffer serialized = ByteBuffer.wrap(oldJson.getBytes(StandardCharsets.UTF_8));

        Object deserialized = valueSerializer.read(serialized);

        ProductPageResponse page = (ProductPageResponse) deserialized;
        assertNull(page.content().get(0).sellerTrustedBadge());
    }

    @Test
    @DisplayName("productDetail 캐시는 non-serializable record(ProductResponse, 중첩 PriceTierResponse 포함)도 왕복 직렬화한다")
    void productDetailCache_serializesNonSerializableRecordAsJson() {
        CacheConfig cacheConfig = new CacheConfig();
        RedisCacheManager.RedisCacheManagerBuilder builder =
                RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(new LettuceConnectionFactory());

        cacheConfig.productDetailCacheCustomizer().customize(builder);

        Optional<RedisCacheConfiguration> productDetailConfig =
                builder.getCacheConfigurationFor(CacheConfig.PRODUCT_DETAIL_CACHE);
        assertTrue(productDetailConfig.isPresent());

        RedisSerializationContext.SerializationPair<Object> valueSerializer =
                productDetailConfig.get().getValueSerializationPair();

        ProductResponse original = new ProductResponse(
                1L, 2L, "테스트판매자", "제주 감귤 5kg", "직접 재배한 감귤", 25000, 10,
                List.of(new PriceTierResponse(2, 22000), new PriceTierResponse(10, 15000)),
                LocalDateTime.now(), null, false, "dummy-test-kakao-js-key", ProductCategory.FOOD, null, false, null, null, List.of());

        ByteBuffer serialized = valueSerializer.write(original);
        Object deserialized = valueSerializer.read(serialized);

        assertEquals(original, deserialized);
    }
}
