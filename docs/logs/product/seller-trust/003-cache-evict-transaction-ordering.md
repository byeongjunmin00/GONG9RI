# 003-cache-evict-transaction-ordering — 캐시 무효화/트랜잭션 순서 고정 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: `CacheConfig`의 `@EnableCaching`에 `order = Ordered.HIGHEST_PRECEDENCE` 지정 — 트랜잭션
  어드바이저 기본값(`LOWEST_PRECEDENCE`)보다 캐싱 어드바이저를 항상 바깥쪽에 둬서, `@Transactional` +
  `@CacheEvict`를 함께 쓰는 모든 메서드(`ReviewService`, `ProductService`)에서 "트랜잭션 커밋 → 캐시
  무효화" 순서를 구조적으로 보장. `docs/policy/caching.md`에 근거 기록. 순서를 직접 증명하는
  `config/CacheEvictionOrderingTest`(신규) 작성 — `BeanFactoryCacheOperationSourceAdvisor`/
  `BeanFactoryTransactionAttributeSourceAdvisor` 두 빈의 `getOrder()`를 비교.
- 결과: `./gradlew test` 전체 통과(391케이스, 실패/에러 0).
- 증거(테스트 결과 요약):
  - `CacheEvictionOrderingTest`: 1케이스 통과 — 캐싱 어드바이저 order < 트랜잭션 어드바이저 order 확인.
  - 회귀 확인: `CacheConfigTest`(3), `ReviewCachingTest`(1), `ProductCachingTest` 전부 통과.
- 참고: 앱 전체의 모든 캐시 무효화 타이밍에 영향을 주는 설정 변경이라 전체 스위트(391케이스)로
  회귀 여부를 확인했다(부분 실행이 아니라 `./gradlew test` 전체 실행).
