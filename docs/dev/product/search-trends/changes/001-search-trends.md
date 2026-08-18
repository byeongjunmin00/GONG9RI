# 실시간 인기 검색어 추가

대상: product/search-trends
담당: 민병준

## 배경 / 요구

와디즈 참고 화면의 검색창 옆 인기 검색어 목록에서 착안. "욕심나는 것들 싹 다 해보자"는 사용자 요청으로
착수(순서는 위임받아 직접 정함, 찜·오픈예정·판매자 신뢰 배지 다음 마지막 네 번째). 새 통계 테이블을
만들지 않고 이미 있는 Redis 인프라(rate-limit·로그인 시도 제한과 동일한 `StringRedisTemplate` 직접
사용 패턴)를 재사용하는 쪽으로 스코프를 좁힘(design.md 참고).

## 설계

`docs/dev/product/search-trends/design.md` 참고 — 날짜별 Redis ZSET(`search-trend:{yyyyMMdd}`, TTL 2일)에
검색어 빈도를 집계하고, `GET /api/products/search-trends`로 상위 N개를 순위 순서로 노출.

## 태스크

- [x] `SearchTrendService`(`recordSearch`/`topTrends`, fail-open)
- [x] `ProductService.list()`에 검색 집계 훅
- [x] `GET /api/products/search-trends?limit=` + `SearchTrendResponse`
- [x] 검색창 아래 인기 검색어 순위 목록(클릭 시 바로 재검색)
- [x] 테스트: 빈도순 정렬, limit 적용, 공백/null 무시, trim 후 합산, 컨트롤러 라우팅(`/search-trends`가
      `/{productId}`보다 우선 매칭됨) 각각
- [x] XSS 방지 검토(검색어를 `textContent`로만 렌더링)

## 평가(통과) 기준

- `./gradlew test` 통과
- 로컬 실서버로 여러 키워드 검색 → `/api/products/search-trends` 빈도순 정렬 실측, `/api/products/999999`가
  여전히 정상적으로 404를 내는지(라우팅 충돌 없음) 확인
