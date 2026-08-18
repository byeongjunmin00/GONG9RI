package com.gong9ri.gong9ri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 실시간 인기 검색어(product/search-trends) — 실제 Redis 대상으로 검증한다. Redis는 JPA 트랜잭션
 * 롤백 범위 밖이라 {@code @BeforeEach}/{@code @AfterEach}에서 직접 정리한다(LoginAttemptGuardTest와
 * 동일한 관례).
 */
@SpringBootTest
class SearchTrendServiceTest {

    private static final String TODAY_KEY =
            "search-trend:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Autowired
    private SearchTrendService searchTrendService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUpBefore() {
        redisTemplate.delete(TODAY_KEY);
    }

    @AfterEach
    void cleanUpAfter() {
        redisTemplate.delete(TODAY_KEY);
    }

    @Test
    @DisplayName("검색 횟수가 많은 키워드일수록 상위로 정렬된다")
    void topTrends_ordersByFrequencyDescending() {
        searchTrendService.recordSearch("감귤");
        searchTrendService.recordSearch("감귤");
        searchTrendService.recordSearch("감귤");
        searchTrendService.recordSearch("보조배터리");
        searchTrendService.recordSearch("보조배터리");
        searchTrendService.recordSearch("텀블러");

        List<String> trends = searchTrendService.topTrends(5);

        assertEquals(List.of("감귤", "보조배터리", "텀블러"), trends);
    }

    @Test
    @DisplayName("limit보다 검색어 종류가 많으면 상위 limit개만 반환한다")
    void topTrends_respectsLimit() {
        searchTrendService.recordSearch("A");
        searchTrendService.recordSearch("A");
        searchTrendService.recordSearch("B");
        searchTrendService.recordSearch("C");

        List<String> trends = searchTrendService.topTrends(1);

        assertEquals(List.of("A"), trends);
    }

    @Test
    @DisplayName("공백이거나 null인 키워드는 집계하지 않는다")
    void recordSearch_ignoresBlankOrNullKeyword() {
        searchTrendService.recordSearch("");
        searchTrendService.recordSearch("   ");
        searchTrendService.recordSearch(null);

        assertTrue(searchTrendService.topTrends(5).isEmpty());
    }

    @Test
    @DisplayName("검색어 앞뒤 공백은 제거하고 같은 키워드로 합산한다")
    void recordSearch_trimsKeywordBeforeAggregating() {
        searchTrendService.recordSearch("감귤");
        searchTrendService.recordSearch("  감귤  ");

        List<String> trends = searchTrendService.topTrends(5);

        assertEquals(List.of("감귤"), trends);
    }
}
