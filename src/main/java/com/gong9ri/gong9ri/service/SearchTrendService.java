package com.gong9ri.gong9ri.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/**
 * 실시간 인기 검색어(product/search-trends) — {@code common/filter/RateLimitFilter}·
 * {@code LoginAttemptGuard}와 같은 인프라(Redis, StringRedisTemplate 직접 사용)를 재사용해서
 * 검색어 빈도를 집계한다. 새 테이블을 만들지 않고 순위 계산에 딱 맞는 자료구조(ZSET)만 쓴다.
 *
 * <p>키를 날짜(yyyyMMdd) 단위로 쪼갠다 — 그래야 "실시간"이라는 이름에 맞게 오늘 하루 검색된 것만
 * 반영되고, 예전에 반짝 유행했던 검색어가 계속 순위에 눌러앉지 않는다. TTL(2일)로 자연 소멸시켜서
 * 별도 배치/스케줄러 없이 정리된다.
 *
 * <p>fail-open 원칙(RateLimitFilter와 동일): Redis 장애 시 기록은 조용히 스킵하고, 조회는 빈 목록을
 * 반환한다 — 인기 검색어는 참고용 UI 요소일 뿐이라 Redis 장애가 검색 자체를 막아서는 안 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchTrendService {

    private static final String KEY_PREFIX = "search-trend:";
    private static final Duration TTL = Duration.ofDays(2);
    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    public void recordSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String normalized = keyword.trim();
        try {
            String key = todayKey();
            redisTemplate.opsForZSet().incrementScore(key, normalized, 1);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("인기 검색어 집계용 Redis 호출 실패, 기록 안 됨: {}", e.getMessage());
        }
    }

    public List<String> topTrends(int limit) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(todayKey(), 0, limit - 1L);
            if (tuples == null) {
                return List.of();
            }
            return tuples.stream().map(ZSetOperations.TypedTuple::getValue).toList();
        } catch (Exception e) {
            log.warn("인기 검색어 집계용 Redis 호출 실패, 빈 목록 반환: {}", e.getMessage());
            return List.of();
        }
    }

    private String todayKey() {
        return KEY_PREFIX + LocalDate.now().format(DATE_KEY_FORMAT);
    }
}
