# 캐시 무효화(@CacheEvict)가 트랜잭션 커밋 이후에만 실행되도록 순서 고정

대상: product/seller-trust
담당: 전용운

## 배경 / 요구

코드리뷰(2026-08-20, 병합된 15개 커밋 리뷰)에서 발견: `ReviewService.create/update/delete`는
`@Transactional`과 `@Caching(evict=...)`를 같은 메서드에 함께 쓰는데, 이 프로젝트엔
`@EnableTransactionManagement`/`@EnableCaching`에 명시적 `order`가 없어(`CacheConfig`엔 `@EnableCaching`만
있고 order 미지정) 두 AOP 어드바이저 순서가 스프링 기본값(`Ordered.LOWEST_PRECEDENCE` 동률)에
맡겨져 있다. 만약 캐시 무효화 어드바이스가 트랜잭션 어드바이스보다 안쪽(트랜잭션 커밋보다 먼저
실행)이면, 커밋 전에 캐시가 비워지고 그 틈에 동시 요청이 커밋 전 값으로 캐시를 다시 채울 수 있다 —
바로 이 커밋이 고치려던 "리뷰 써도 배지가 최대 30분간 안 뜨는" 버그가 좁은 레이스로 재발할 수 있다.
`ProductService`도 같은 패턴(`@Transactional` + `@CacheEvict` 같은 메서드)을 이미 쓰고 있어 영향 범위는
이번 리뷰 캐시뿐 아니라 상품 캐시 전체다.

## 설계

특정 서비스 메서드를 고치는 대신, **캐싱 어드바이저를 트랜잭션 어드바이저보다 항상 바깥쪽에 두도록
`CacheConfig`의 `@EnableCaching`에 명시적 `order`를 고정**한다 — 이러면 실행 순서가
"트랜잭션 시작 → (안쪽) 실제 메서드 실행 → 트랜잭션 커밋 → (바깥쪽) 캐시 무효화"로 항상 보장되어,
`ReviewService`뿐 아니라 `ProductService` 등 앞으로 추가되는 모든 `@CacheEvict` 호출부에 동일하게
적용된다(개별 메서드마다 신경 쓸 필요 없음 — 코드리뷰가 지적한 "구조적으로 막는 장치가 없다"는
문제를 서비스 코드가 아니라 설정 레벨에서 근본적으로 없앤다).

## 태스크

- [x] `CacheConfig`의 `@EnableCaching`에 `order = Ordered.HIGHEST_PRECEDENCE` 지정(트랜잭션 어드바이저
      기본값 `LOWEST_PRECEDENCE`보다 캐싱을 항상 바깥쪽에 둠)
- [x] `docs/policy/caching.md`에 이 순서 보장을 근거로 기록
- [x] 순서 보장을 검증하는 테스트 추가

## 평가(통과) 기준

- 신규 순서 검증 테스트 통과
- 기존 `ReviewCachingTest`, `ProductCachingTest` 등 캐싱 관련 테스트 전체 통과

## 실행 결과

계획대로 `CacheConfig`의 `@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)`를 지정했다 — 트랜잭션
어드바이저는 기본값(`LOWEST_PRECEDENCE`)을 그대로 두므로, 캐싱 어드바이저가 항상 더 바깥쪽에 놓여
"트랜잭션 커밋 → 캐시 무효화" 순서가 구조적으로 보장된다.

순서 보장 자체을 검증하는 방법은 계획 당시 생각했던 "롤백 시 무효화 안 됨" 시나리오 대신(그건
`@CacheEvict`의 기본 동작 자체를 검증하는 것이지 두 어드바이저의 순서를 직접 증명하지 않는다), 실제
스프링 컨텍스트에서 `BeanFactoryCacheOperationSourceAdvisor`/`BeanFactoryTransactionAttributeSourceAdvisor`
두 빈을 직접 꺼내 `getOrder()`를 비교하는 `CacheEvictionOrderingTest`로 더 직접적으로 증명했다(설정이
나중에 실수로 지워지면 즉시 실패하는 회귀 테스트).

`./gradlew test` **전체 391케이스 통과**(신규 1케이스 포함, 실패/에러 0). 기존
`ReviewCachingTest`/`ProductCachingTest`/`CacheConfigTest`에도 회귀 없음.

## 리스크 / 전제

- 이 변경은 `CacheConfig` 하나만 고쳐서 앱 전체의 모든 `@CacheEvict`/`@Cacheable` 호출부에 적용된다
  (`ReviewService`뿐 아니라 `ProductService` 등도 동일하게 이제 "커밋 후 무효화"가 보장됨) — 범위가
  넓은 설정 변경이라, 혹시 어딘가 캐시 무효화 타이밍에 암묵적으로 의존하던 코드가 있다면 영향을 받을
  수 있다. 전체 테스트 스위트(391케이스)가 그런 암묵적 의존을 검증하는 유일한 안전망이며, 이번엔
  회귀가 없었다.
- 로컬 검증에 MySQL + Redis 가동 필요.

