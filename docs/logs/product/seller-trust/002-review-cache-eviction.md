# 002-review-cache-eviction — 리뷰 작성 시 상품 캐시 무효화 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: `ReviewService.create/update/delete`에 `@Caching(evict = {...})` 추가 — 상품 상세 캐시는
  `allEntries = true`로 날린다(배지가 판매자 전체 상품 리뷰를 합산해 판정하므로 상품 A에 리뷰가
  달리면 같은 판매자의 상품 B·C 배지도 함께 바뀌어야 함).
- 결과: `./gradlew test` 전체 통과. `ReviewCachingTest` 신규 케이스 통과.
- 증거: 무효화 코드를 일부러 빼고 돌려 `ReviewCachingTest.java:82`에서 실제로 실패하는 것까지
  역검증함(테스트가 실제로 이 버그를 잡는다는 것을 증명). 원인 진단 단계에서는 목록 캐시 키가
  page+size 조합이라는 점을 이용해 `size=37`/`size=41`(캐시 미스, `sellerTrustedBadge=true`)과
  `size=50`(캐시 히트, `false`)을 비교해 "집계 로직은 정상, 캐시만 낡음"을 먼저 확정한 뒤 수정에
  들어감.
