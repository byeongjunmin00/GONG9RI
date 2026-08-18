# 실시간 인기 검색어 (product/search-trends) — Design

## 개요

와디즈 검색창 옆 순위 목록에서 착안. 새 DB 테이블 없이 이미 있는 Redis 인프라(`common/filter/RateLimitFilter`,
`common/security/LoginAttemptGuard`와 동일하게 `StringRedisTemplate` 직접 사용)를 재사용해 검색어 빈도를
집계한다.

## 자료구조 / 키 설계

- `search-trend:{yyyyMMdd}` — Redis **ZSET**(member=검색어, score=누적 검색 횟수).
- 키를 **날짜 단위로 쪼갠다** — "실시간"이라는 이름에 맞게 오늘 검색된 것만 반영되고, 예전에 반짝
  유행했던 검색어가 계속 순위에 눌러앉지 않는다.
- TTL 2일 — 별도 배치/스케줄러 없이 자연 소멸(자정을 넘겨도 당일 자정 직후 잠깐은 어제 키가 남아있을
  수 있게 여유를 둠, 어차피 오늘 키를 조회하므로 실제 응답에는 영향 없음).

## 언제 집계하는가

`ProductService.list(page, size, category, sort, keyword)` 안, keyword가 있을 때만
`SearchTrendService.recordSearch(keyword)` 호출. 이 메서드는 keyword가 있으면
`@Cacheable(condition = "#keyword == null || #keyword.isBlank()")`로 캐시를 안 타서(product/list-search)
실제 검색마다 항상 메서드 본문이 실행된다 — 집계 누락 걱정 없이 이 지점 하나에서만 기록하면 된다.

## API

- `GET /api/products/search-trends?limit=5` — 상위 N개 검색어를 순위 순서로 반환(`SearchTrendResponse.keywords`).
  검색 횟수 숫자는 응답에 포함하지 않는다(와디즈 참고 화면처럼 순위만 노출, 프론트가 인덱스로 번호를 매김).
- 경로가 `GET /api/products/{productId}`와 같은 컨트롤러 아래 있지만, Spring의 경로 매칭은 리터럴
  세그먼트(`search-trends`)를 변수 세그먼트(`{productId}`)보다 항상 더 구체적으로 취급해 우선 매칭한다
  (선언 순서와 무관) — `ProductControllerTest`에 이 라우팅 자체를 검증하는 테스트를 따로 둠.

## fail-open

`RateLimitFilter`/`LoginAttemptGuard`와 같은 원칙: Redis 호출이 실패하면 기록은 조용히 스킵하고,
조회는 빈 목록을 반환한다. 인기 검색어는 참고용 UI 요소일 뿐이라 Redis 장애가 검색 자체를 막아서는
안 된다.

## 프론트

- `index.html`의 검색창(`#search-form`) 바로 아래 `#search-trends` — 페이지 로드 시 + 검색 제출 시마다
  다시 불러온다(방금 검색한 키워드가 곧바로 순위에 반영되므로).
- 키워드를 클릭하면 그 키워드로 바로 재검색(`searchInputEl.value` 채우고 `submitSearch()` 호출).
- **XSS 주의**: 검색어는 다른 방문자가 검색창에 직접 입력한 임의 문자열이 Redis에 그대로 저장돼
  나중에 모든 방문자에게 노출되는 구조다(저장형 XSS 표면). `main.js`의 `renderSearchTrends()`는
  반드시 `textContent`/`document.createTextNode`로만 렌더링하고 `innerHTML`로 조립하지 않는다.

## 관련 코드

`service/SearchTrendService.java`, `dto/SearchTrendResponse.java`, `controller/ProductController.java`
(`GET /search-trends`), `service/ProductService.list()`, `static/index.html`(`#search-trends`),
`static/js/main.js`(`loadSearchTrends`/`renderSearchTrends`/`submitSearch`), `static/css/components.css`
(`.search-trends*`).
