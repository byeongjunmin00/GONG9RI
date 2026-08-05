# 002-caching — 상품 목록·상세(product/list, product/detail) Redis 캐싱 (로그)

## Attempt 1 — 2026-08-05

- 시도: `docs/dev/ongoing/product-list-detail-caching.md` 계획대로 상품 목록·상세 캐싱을 구현했다. `docs/logs/mypage/view/002-caching.md`(Attempt 1·2)에서 확정된 결함(운영 설정의 기본 Redis 직렬화기가 non-serializable record를 요구해 깨지는 문제, 범용 JSON 직렬화기 사용 시 캐시 히트 시 타입이 `LinkedHashMap`으로 소실되는 문제)을 처음부터 피하기 위해, 이번에도 캐시별로 타입을 고정한 `JacksonJsonRedisSerializer<>(대상클래스.class)`를 값 직렬화기로 명시하는 기존 패턴(`sellerRevenueCacheCustomizer`)을 그대로 재사용했다.
  - `CacheConfig.java`에 캐시 2개 추가: `PRODUCT_LIST_CACHE`("productList", 값 타입 `ProductPageResponse`), `PRODUCT_DETAIL_CACHE`("productDetail", 값 타입 `ProductResponse`). 각각 `productListCacheCustomizer()`/`productDetailCacheCustomizer()` `RedisCacheManagerBuilderCustomizer` 빈으로 분리해 `sellerRevenueCacheCustomizer`와 동일한 구조(캐시 이름별 `RedisCacheConfiguration` + 타입 고정 직렬화기)를 유지했다. TTL은 목록/상세 모두 30분(`PRODUCT_LIST_TTL`/`PRODUCT_DETAIL_TTL`)으로 상수화했다 — `sellerRevenue`(10분)보다 길게 잡은 이유는, 이번 캐시는 무효화가 "전체 무효화" 방식이라(정렬 조건이 없어 신규/변경 상품이 어느 페이지에 들어갈지 특정 불가) 세밀하게 무효화되는 `sellerRevenue`보다 캐시 적중 기간의 가치가 상대적으로 크다고 판단했기 때문(계획 문서에 구체 TTL 값은 없어 이번에 판단해 결정).
  - `ProductService.list(page, size)`: `@Cacheable(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, key = "#page + '-' + #size")` 적용.
  - `ProductService.detail(productId)`: `@Cacheable(cacheNames = CacheConfig.PRODUCT_DETAIL_CACHE, key = "#productId")` 적용.
  - `ProductService.register(...)`: `@CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)` 적용. `mypage/seller-revenue`(`PaymentService`/`TeamDeadlineService`)와 달리 `CacheManager` 직접 호출 대신 애노테이션을 택한 이유는, 그쪽은 무효화 대상 키(sellerId)가 메서드 파라미터가 아니라 메서드 본문에서 조회한 지역변수라 `@CacheEvict`의 SpEL로 표현 불가능했던 반면, `ProductService`의 `update`/`delete`는 `productId`가 애초에 메서드 파라미터이고 `register`는 애초에 "전체 무효화"라 SpEL 제약이 문제되지 않기 때문 — 계획 문서에서도 이 경우 애노테이션 방식이 가능하다고 명시했고, `ProductService` 파일 내에서는 3개 메서드(register/update/delete) 모두 애노테이션으로 일관되게 통일했다.
  - `ProductService.update(...)`: `@Caching(evict = {@CacheEvict(cacheNames = PRODUCT_DETAIL_CACHE, key = "#productId"), @CacheEvict(cacheNames = PRODUCT_LIST_CACHE, allEntries = true)})` 적용 — 서로 다른 무효화 범위(특정 키 vs 전체)를 가진 캐시 2개를 하나의 메서드에서 함께 지우기 위해 `@Caching`으로 묶었다.
  - `ProductService.delete(...)`: update와 동일한 `@Caching` 조합 적용.
  - `src/test/java/com/gong9ri/gong9ri/service/ProductCachingTest.java` 신규 작성. `SellerRevenueCachingTest`의 패턴(의미 있는 규모의 더미 데이터 + 무효화 경로를 거치지 않고 레포지토리에 직접 데이터를 꽂아 넣어 캐시가 진짜로 이전 값을 반환하는지 증명)을 그대로 따랐다: 판매자 1명 아래 `DUMMY_PRODUCT_COUNT`(15)개의 더미 상품을 심고, `@MockitoSpyBean private ProductRepository`로 5개 테스트 작성 —
    1. 동일 page/size 반복 조회 시 `findAllWithSeller` 1회만 호출(캐시 히트) — 첫 조회 후 레포지토리에 직접 상품을 추가해도 재조회 결과(`totalElements`)가 그대로임을 확인.
    2. 동일 productId 상세 반복 조회도 동일 패턴(엔티티를 직접 수정 후 저장해도 재조회 결과가 그대로임)으로 캐시 히트 증명, `findByIdWithSeller` 1회만 호출 확인.
    3. `register(...)` 후 목록 재조회 시 신규 상품이 반영됨(무효화 확인), `findAllWithSeller` 2회 호출 확인.
    4. `update(...)` 후 해당 상품 상세·목록 재조회 시 최신 이름/가격 반영 확인. `findByIdWithSeller`는 3회 호출됨을 확인했다(첫 detail 조회 1회 + update 내부의 `findProductWithSeller`(소유자 검증, 캐시와 무관한 일반 조회) 1회 + 무효화 후 detail 재조회 1회) — 처음엔 2회로 기대했다가 실제 실행 결과로 이 내부 호출을 발견해 기대치를 3회로 수정했다.
    5. `delete(...)` 후 목록 재조회 시 해당 상품이 빠지고, 상세 재조회 시 `BusinessException`(`ErrorCode.PRODUCT_NOT_FOUND`) 발생 확인(`assertThrows`).
  - 구현 도중 실제 실행에서 결함 하나를 발견해 즉시 고쳤다: 목록 캐시의 키가 `page`+`size` 조합뿐이라 seller/더미데이터와 무관한데, 처음엔 5개 테스트 모두 같은 상수(`LIST_PAGE=0, LIST_SIZE=30`)를 캐시 키로 썼다. 이 프로젝트 테스트는 `@SpringBootTest`로 Spring 컨텍스트(캐시 빈 포함)를 여러 테스트 메서드/클래스가 공유하고, DB는 `@Transactional`로 테스트별 롤백되지만 **캐시 빈(싱글톤)은 롤백되지 않아** 같은 키를 쓰는 이전 테스트 메서드의 캐시값이 다음 테스트로 새어 들어가는 문제(cache pollution)가 실제로 재현됐다(4개 테스트 FAIL, `totalElements` 불일치 등). 각 테스트 메서드가 서로 다른 `size` 값(101~104)을 써서 캐시 키를 격리하는 방식으로 수정해 해결했다.
  - `CacheConfigTest.java`(기존 파일)에 신규 캐시 2개용 케이스 추가: `productListCache_serializesNonSerializableRecordAsJson`(중첩 `ProductSummaryResponse`를 포함한 `ProductPageResponse` 왕복 직렬화 검증), `productDetailCache_serializesNonSerializableRecordAsJson`(중첩 `PriceTierResponse` 리스트를 포함한 `ProductResponse` 왕복 직렬화 검증) — 기존 `sellerRevenueCache` 테스트와 동일하게 `LettuceConnectionFactory`(연결 시도 없음) + `RedisCacheManagerBuilder`로 실제 Redis 서버 없이 직렬화기 설정 자체를 검증하는 순수 단위 테스트 방식을 유지했다.
- 결과:
  - `./gradlew compileJava compileTestJava` → 성공.
  - `./gradlew test --tests "com.gong9ri.gong9ri.service.ProductCachingTest" --tests "com.gong9ri.gong9ri.config.CacheConfigTest" --tests "com.gong9ri.gong9ri.controller.ProductControllerTest"` → **BUILD SUCCESSFUL**. `ProductCachingTest` 5/5, `CacheConfigTest` 3/3(기존 1 + 신규 2), `ProductControllerTest`(회귀) 12/12.
  - `./gradlew build`(전체 스위트) → **BUILD SUCCESSFUL**. `build/test-results/test/*.xml` 합산: `tests=83, failures=0, errors=0`.
  - 로컬 Docker `gong9ri-mysql`(3306)·`gong9ri-redis`(6379) 실제 가동 상태에서 검증(별도 인프라 이슈 없음 — 이미 이전 라운드에서 구성 완료된 환경 재사용).
- 원인: (해당 없음 — 최종 결과 기준 실패 없음. 구현 도중 발견·즉시 수정한 캐시 키 충돌 이슈는 위 "시도"에 원인·해결 함께 기록)
- 증거:
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.service.ProductCachingTest.xml`: `tests="5" failures="0" errors="0"`
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.config.CacheConfigTest.xml`: `tests="3" failures="0" errors="0"`
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.controller.ProductControllerTest.xml`: `tests="12" failures="0" errors="0"`
  - 저장소 전체 `build/test-results/test/*.xml` 합산: `tests=83 failures=0 errors=0`
  - `docker ps`: `gong9ri-mysql`(mysql:8, 3306), `gong9ri-redis`(redis:7, 6379) 정상 기동 확인.
- 다음: (Evaluate 단계 몫)

## Evaluate — 2026-08-05  ✅ PASS

- 결과 (계산적 평가, 직접 재실행):
  - `./gradlew compileJava compileTestJava` → `BUILD SUCCESSFUL`(UP-TO-DATE, 변경 없음 재확인).
  - `./gradlew test --tests "com.gong9ri.gong9ri.service.ProductCachingTest" --tests "com.gong9ri.gong9ri.config.CacheConfigTest" --tests "com.gong9ri.gong9ri.controller.ProductControllerTest" --rerun` → `BUILD SUCCESSFUL`. XML 확인: `ProductCachingTest` `tests="5" failures="0" errors="0"`, `CacheConfigTest` `tests="3" failures="0" errors="0"`, `ProductControllerTest` `tests="12" failures="0" errors="0"`.
  - `./gradlew build --rerun` → `BUILD SUCCESSFUL`. 저장소 전체 `build/test-results/test/*.xml`(13개 클래스) 합산: `tests=83 failures=0 errors=0`.
  - 로컬 Docker `gong9ri-mysql`(3306)·`gong9ri-redis`(6379) 정상 기동 상태에서 검증. DB/스키마 이슈로 인한 실패는 없었음(전부 로직/직렬화 검증이며 인프라 문제 아님).
- 결과 (추론적 평가):
  1. **`docs/policy/caching.md` 준수**: 캐싱 로직이 `ProductService`(Service 계층)에만 있음을 `git diff`로 확인 — `Controller`/`Repository`는 변경 이력 없음. TTL 30분이 목록·상세 각각 안전장치로 설정돼 있어 정책의 "무효화가 항상 성공한다고 전제하지 않는다" 요건 충족.
  2. **`docs/code-convention.md` 준수**: 생성자 주입(`@RequiredArgsConstructor` + `final` 필드) 유지, 변경 없음. 위반 없음.
  3. **계획(`docs/dev/ongoing/product-list-detail-caching.md`) 일치**: `list`/`detail` 캐시 키, `register`(목록 전체 무효화)/`update`/`delete`(상세 productId + 목록 전체 무효화) 범위가 계획 문서와 정확히 일치함을 `git diff` 라인 단위로 확인. Controller·Repository는 실제로 손대지 않았고, 검색/정렬 파라미터 등 계획 외 범위 변경도 없음.
  4. **직렬화 검증 실질성**: `CacheConfigTest` 신규 케이스 2개는 형식적 존재 확인이 아니라 중첩 record(`ProductSummaryResponse` in `ProductPageResponse`, `PriceTierResponse` 리스트 in `ProductResponse`)를 실제 값으로 채워 `valueSerializer.write`→`read` 왕복 후 `assertEquals(original, deserialized)`로 검증 — 이전 결함(non-serializable 예외, 타입 소실로 인한 `LinkedHashMap`/`ClassCastException`)이 재발하면 실제로 실패하는 실질적 테스트로 판단.
  5. **캐시 히트 테스트 실질성**: `ProductCachingTest`의 히트 테스트 2건 모두 `mypage/seller-revenue` 패턴을 그대로 따름 — 첫 조회 후 **무효화 경로(register/update/delete)를 거치지 않고** 레포지토리에 직접 데이터를 추가/수정(`saveProduct`, `product.update()+save`)한 뒤 재조회 결과가 그 변경을 반영하지 않고 이전 값과 `assertEquals`로 동일함을 확인 — 레포지토리 호출 횟수(`times(1)`)만 보는 형식적 검증이 아니라 "진짜로 stale한 캐시값이 반환되는지"까지 증명하는 방식. 실질적 검증으로 판단.
  6. **캐시 오염 이슈 해결 방식 판단**: 목록 캐시 키가 `page`+`size` 조합뿐이라 `sellerRevenue`(sellerId 기반, 테스트마다 자연히 유니크)와 달리 테스트 데이터와 무관하다. `@SpringBootTest`로 캐시 빈(싱글톤)이 테스트 메서드 간 공유되고 `@Transactional` DB 롤백과 무관하게 캐시 상태가 남는 구조적 특성상, 테스트 메서드마다 `size`를 101~104로 다르게 박아 키를 격리한 해결은 **현재 시점엔 동작하지만 근본적 해결은 아니고 수작업 조율에 의존하는 임시방편**으로 판단함 — 새 테스트가 추가될 때 기존에 쓰인 size 값을 몰라서 재사용하면 다시 오염이 재현될 수 있는 구조적 취약점이 남아있음(예: `@BeforeEach`/`@AfterEach`에서 `CacheManager.getCache(...).clear()` 하는 방식이 더 근본적이었을 것). 다만 이 판단은 Evaluate 권한 밖의 "해결책 처방"이 아니라 현재 구현의 견고성 평가이며, 현재 테스트 스위트 자체는 통과하므로 통과 판정에는 영향 없음(추후 리스크로 남김).
  7. **`update()` 테스트의 `findByIdWithSeller` 3회 기대치 판단**: 코드 확인 결과, `update()`/`delete()` 내부의 `findProductWithSeller(productId)`는 `@Cacheable`이 붙은 `detail()` 메서드를 거치지 않고 `productRepository.findByIdWithSeller`를 **직접** 호출하는, 캐싱과 무관한 오너십 검증용 평범한 DB 조회다(캐싱 추가 전부터 존재했던 흐름). 따라서 `detailBefore`(캐시 미스 1회) + `update()` 내부 오너십 검증(1회) + `detailAfter`(무효화 후 캐시 미스 1회) = 3회는 실행 결과에 기대치를 억지로 끼워맞춘 것이 아니라 실제 애플리케이션 동작을 정확히 반영한 타당한 수치로 판단함. 이 카운트는 여전히 무효화 검증 신호로 유효하다 — 무효화가 안 됐다면 `detailAfter`가 캐시를 히트해 총 호출이 2회에 그쳤을 것이기 때문.
  8. `git diff`로 전체 변경 범위 확인: `CacheConfig.java`, `ProductService.java`, `CacheConfigTest.java`(수정) + `ProductCachingTest.java`(신규)만 변경됨. `ProductController`/`ProductRepository`/검색·정렬 파라미터 등 계획 외 범위 변경 없음.
- 원인: (해당 없음 — 실패 없음)
- 증거:
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.service.ProductCachingTest.xml`: `tests="5" failures="0" errors="0"`
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.config.CacheConfigTest.xml`: `tests="3" failures="0" errors="0"`
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.controller.ProductControllerTest.xml`: `tests="12" failures="0" errors="0"`
  - 저장소 전체 `build/test-results/test/*.xml`(13개 클래스) 합산: `tests=83 failures=0 errors=0`
  - `docker ps`: `gong9ri-mysql`(mysql:8, 3306), `gong9ri-redis`(redis:7, 6379) 정상 기동 확인.
- 판정: **PASS** — `docs/dev/product/crud/design.md` 갱신 완료, `docs/dev/ongoing/product-list-detail-caching.md` → `docs/dev/product/crud/changes/002-caching.md` 채번 이동 완료.
