# 002-caching — 판매자 수익 현황(mypage/seller-revenue) Redis 캐싱 (로그)

## Attempt 1 — 2026-08-05  ❌ FAIL

- 시도: `docs/dev/ongoing/mypage-seller-revenue-caching.md` 계획대로 Redis 캐싱을 최초 도입했다. Spring Cache 추상화(`@EnableCaching`/`@Cacheable`) + `RedisCacheManagerBuilderCustomizer`로 TTL(10분)만 커스터마이즈하는 방식을 택해 별도 `RedisCacheManager` 빈을 직접 만들지 않았다 — 이렇게 하면 `spring.cache.type` 프로퍼티만으로 캐시 구현체를 스위칭할 수 있어(운영: `redis`, 테스트: `simple`/`ConcurrentMapCacheManager`), 로컬에 실제 Redis 없이도 기존 `@SpringBootTest` 스위트와 신규 캐싱 테스트가 돌아가게 했다.
  - `build.gradle`에 `spring-boot-starter-cache`, `spring-boot-starter-data-redis` 추가.
  - `src/main/resources/application.yaml`에 `spring.data.redis.host/port`(환경변수 오버라이드, 기존 datasource 패턴과 동일)와 `spring.cache.type: redis` 추가.
  - `src/test/resources/application.yaml` 신규 작성(기존 main `application.yaml`과 동일한 datasource/jpa 설정 + `spring.cache.type: simple` 오버라이드) — Gradle 테스트 클래스패스에서 테스트 리소스가 메인 리소스보다 우선하는 성질을 이용해, 테스트에서만 인메모리 캐시를 쓰게 했다.
  - `com.gong9ri.gong9ri.config.CacheConfig` 신규: `@EnableCaching` + `SELLER_REVENUE_CACHE`("sellerRevenue") 캐시 이름 상수 + `RedisCacheManagerBuilderCustomizer` 빈으로 해당 캐시에만 TTL 10분을 건다 (`docs/policy/caching.md`의 TTL 안전장치 요구 반영).
  - `SellerMypageService.revenue(principal)`에 `@Cacheable(cacheNames = CacheConfig.SELLER_REVENUE_CACHE, key = "#principal.member.id")` 적용. 키를 principal 객체가 아닌 sellerId(Long)로 명시. `requireSeller(principal)` 검증은 메서드 본문에 그대로 두었다 — 캐시 히트 시 스킵되지만, `Member.role`이 생성 후 변경 불가(세터 없음)라 sellerId 키 하나는 항상 같은 role로 귀결돼 문제되지 않는다고 판단.
  - `PaymentService.create(...)`: 결제 저장 직후 `product.getSeller().getId()`로 해당 판매자 캐시를 무효화. `product`가 메서드 파라미터가 아니라 지역변수라 `@CacheEvict`의 SpEL로는 표현이 안 돼(파라미터만 참조 가능), `CacheManager`를 직접 주입해 `cache.evict(sellerId)`를 명시적으로 호출하는 방식을 택함. teamId 유무와 무관하게 항상 실행.
  - `TeamDeadlineService.processDeadline(teamId)`: `paidPayments`가 비어있지 않을 때만(`!paidPayments.isEmpty()`) `team.getProduct().getSeller().getId()`로 같은 방식(`CacheManager.evict`)으로 무효화. 이 메서드는 팀별 독립 `@Transactional`이라 트랜잭션 내부에서 즉시 무효화되도록 구현(배치 전체 완료 후 일괄 처리 아님).
  - `.github/workflows/ci.yml`: 기존 `mysql` 서비스 컨테이너와 같은 패턴으로 `redis:7` 서비스 컨테이너(포트 6379, `redis-cli ping` 헬스체크) 추가.
  - `src/test/java/com/gong9ri/gong9ri/service/SellerRevenueCachingTest.java` 신규: `@SpringBootTest` + `@Transactional` + `@MockitoSpyBean private PaymentRepository`로 4개 테스트 작성 —
    1. 동일 sellerId 반복 조회 시 `findRevenueSummaryBySellerId` 1회만 호출(캐시 히트)
    2. `paymentService.create(...)` 후 재조회 시 최신 금액 반영 + 레포지토리 2회 호출(무효화 확인)
    3. `teamDeadlineService.processDeadline(...)`로 환불 발생 후 재조회 시 최신 값 반영 + 레포지토리 2회 호출
    4. 환불이 없는 마감 처리는 캐시를 무효화하지 않음(레포지토리 1회만 호출)
  - `./gradlew compileJava`, `./gradlew compileTestJava` 모두 성공 확인. 로컬에 MySQL/Redis 둘 다 가동 중이 아니어서(포트 3306/6379 접속 실패 확인) 이번 단계에서 `./gradlew test`는 실행하지 않았다(Evaluate 단계 몫이자, 기존 `@SpringBootTest` 스위트 자체가 로컬 MySQL 가동을 전제로 하는 구조라 이 시도로 새로 생긴 제약이 아님).

- 결과:
  - `./gradlew compileJava compileTestJava` → 성공 (재확인).
  - `./gradlew test --tests "*SellerMypage*" --tests "*SellerRevenueCaching*"` → **15개 테스트 전부 FAIL** (`SellerMypageControllerTest` 11케이스 + `SellerRevenueCachingTest` 4케이스, 신규 캐싱 테스트 포함). 전부 동일한 원인 체인으로 실패해 `@SpringBootTest` 컨텍스트 로딩 자체가 안 된 것으로, 캐싱 로직의 실제 pass/fail은 이번 실행으로 **확인 불가**.
  - 이와 별개로 추론적 평가(코드 검토)에서 실제 Redis 사용 시 런타임에 깨질 결함을 하나 확정함(아래 원인).
  - 종합 판정: **로직 실패** (계산적 평가는 DB 미가동으로 미결이지만, 추론적 평가에서 확정된 결함이 있어 "DB 미가동으로 판정불가"만으로 분류하지 않음).
- 원인:
  - **계산적 평가 미결 사유**: MySQL 미가동 확인(`Test-NetConnection -ComputerName localhost -Port 3306` → `TcpTestSucceeded: False`), Redis도 로컬 미설치(`-Port 6379` → `False`). Gradle 실패 스택트레이스: `java.lang.IllegalStateException` → `BeanCreationException`(`AbstractAutowireCapableBeanFactory`) → `ServiceException`(`AbstractServiceRegistryImpl`) → `org.hibernate.HibernateException: Unable to determine Dialect without JDBC metadata (please set 'jakarta.persistence.jdbc.url' ...)`(`DialectFactoryImpl.determineDialect`). 이는 DB 연결 실패의 전형적 신호로, 캐싱 로직 자체의 결함이 아니다.
  - **추론적 평가에서 확정한 결함**: `dto/RevenueResponse.java`가 `public record RevenueResponse(Integer totalRevenue, Long paidCount, Long refundedCount)`로 선언돼 있고 `Serializable`을 구현하지 않는다. 운영 프로파일(`src/main/resources/application.yaml`)의 `spring.cache.type: redis`가 실제로 적용될 때, Spring Boot의 Redis 캐시 자동설정(`org.springframework.boot.cache.autoconfigure.RedisCacheConfiguration`)은 값 직렬화기로 기본 `JdkSerializationRedisSerializer`를 사용한다 — 이 값을 오버라이드하는 별도 설정을 `CacheConfig`가 추가하지 않았다(TTL만 커스터마이즈함). JDK 직렬화는 `Serializable` payload를 요구하므로, 실제 Redis에 `revenue()` 응답을 캐싱하려는 시점에 `IllegalArgumentException`(non-serializable payload) 발생이 예상된다.
  - 이 결함이 테스트로 드러나지 않는 이유: `src/test/resources/application.yaml`이 테스트 프로파일에서 `spring.cache.type: simple`(`ConcurrentMapCacheManager`, 직렬화 없음)로 강제 오버라이드하고 있어, 신규 `SellerRevenueCachingTest`를 포함한 어떤 자동화 테스트로도 이 결함을 잡을 수 없다. MySQL/Redis가 정상 가동되어 `./gradlew test`가 전부 통과하더라도 이 결함은 그대로 남아있게 된다.
- 증거:
  - (계산적) gradle 출력: `SellerMypageControllerTest`·`SellerRevenueCachingTest` 15개 케이스 전부 `java.lang.IllegalStateException` → `org.hibernate.HibernateException: Unable to determine Dialect without JDBC metadata` 동일 원인으로 FAIL. `Test-NetConnection -ComputerName localhost -Port 3306`/`-Port 6379` 둘 다 `TcpTestSucceeded: False`.
  - (추론적, 바이트코드 역어셈블로 확인) `javap -c -p` 결과:
    - `org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig(ClassLoader)`: 키 직렬화 `RedisSerializer.string()`, **값 직렬화 `RedisSerializer.java(classLoader)`**(= `JdkSerializationRedisSerializer` 동치) 사용.
    - `org.springframework.boot.cache.autoconfigure.RedisCacheConfiguration`(Spring Boot 4.1.0 자동설정 클래스, `spring.cache.type=redis`일 때 실제 `RedisCacheManager` 빌드 경로): `new JdkSerializationRedisSerializer(classLoader)`를 값 직렬화기로 명시적으로 세팅하는 바이트코드 확인.
    - `dto/RevenueResponse.java`: `record`이며 `implements Serializable` 없음.
  - `CacheConfig.java`(`sellerRevenueCacheCustomizer`)는 `entryTtl(SELLER_REVENUE_TTL)`만 커스터마이즈하고 직렬화기는 건드리지 않음 → 위 기본 직렬화기가 그대로 적용됨.
- 다음: 같은 접근으로 고칠 수 있는 범위로 판단(Generate 재시도 대상, 재계획 불필요) — `RevenueResponse`(및 캐싱 대상이 될 다른 응답 DTO)를 `Serializable`로 만들거나, `CacheConfig`의 `RedisCacheConfiguration`에 JSON 직렬화기(`GenericJackson2JsonRedisSerializer` 등)를 명시적으로 설정. 이후 MySQL/Redis가 실제 가동된 환경에서 `./gradlew test --tests "*SellerMypage*" --tests "*SellerRevenueCaching*"` 재검증 필요.

## Attempt 2 — 2026-08-05

- 시도: Attempt 1에서 확정된 결함(`RevenueResponse`가 non-serializable record인데 운영 설정(`spring.cache.type: redis`)에서 기본 `JdkSerializationRedisSerializer`가 적용돼 캐싱 시점에 런타임 예외 발생)을 고쳤다. 지시대로 `RevenueResponse`를 `Serializable`로 바꾸는 방향은 취하지 않고, `CacheConfig`의 `RedisCacheConfiguration`에 JSON 직렬화기를 명시적으로 설정하는 방향을 택했다.
  - `CacheConfig.sellerRevenueCacheCustomizer()`의 기존 `RedisCacheConfiguration.defaultCacheConfig().entryTtl(...)` 체인에 `.serializeValuesWith(...)`를 추가해 값 직렬화기를 명시했다. TTL(10분) 커스터마이즈와 `SELLER_REVENUE_CACHE`(`"sellerRevenue"`)에만 적용되는 범위는 그대로 유지했다.
  - 처음엔 지시에서 예시로 든 `GenericJackson2JsonRedisSerializer`를 시도했으나, `./gradlew compileJava`에서 `[removal] GenericJackson2JsonRedisSerializer ... has been deprecated and marked for removal` 경고가 떠서, 이 저장소가 이미 Jackson 3(`tools.jackson.databind`, `spring-boot-starter-jackson` 경유)을 쓰는 것을 확인하고 비-지원중단 대체재인 `GenericJacksonJsonRedisSerializer`(Jackson 3 기반, "2" 없는 신규 클래스)로 바꿔 재시도했다.
  - 이 단계에서 단위 테스트(`CacheConfigTest`)로 실제 값 직렬화/역직렬화 왕복을 확인하는 과정에서 범용 직렬화기(타입 정보를 값에 함께 저장하지 않는 `GenericJacksonJsonRedisSerializer`)를 쓰면 역직렬화 결과가 `RevenueResponse`가 아니라 `LinkedHashMap`으로 나오는 것을 발견했다(Spring Cache 추상화의 `Cache.get(key)` 경로가 조회 시 목표 타입을 넘기지 않아, `RedisCache`가 타입 힌트 없이 범용 역직렬화만 수행하기 때문). 이 상태로 두면 캐시 히트 시 `ClassCastException`이 발생해 원래 결함과 성격이 같은(테스트에서 안 드러나고 실제 Redis 사용 시에만 터지는) 새 결함이 될 것으로 판단해, 이 캐시가 `RevenueResponse` 단일 타입만 다룬다는 점을 이용해 타입을 고정한 `JacksonJsonRedisSerializer<>(RevenueResponse.class)`(생성자에 대상 클래스를 명시하는, "Generic"이 아닌 타입 지정 직렬화기)로 최종 변경했다. 이 방식은 값에 타입 메타데이터(`@class` 등)를 함께 저장하는 디폴트 타이핑(polymorphic typing) 없이도 정확한 타입으로 역직렬화되어, 별도의 `PolymorphicTypeValidator` 화이트리스트 설정이 필요 없다.
  - `src/test/java/com/gong9ri/gong9ri/config/CacheConfigTest.java` 신규 작성: `@SpringBootTest` 없이 순수 단위 테스트로 `new CacheConfig()`를 직접 생성하고, `RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(new LettuceConnectionFactory())`(생성 시점에 실제 연결을 맺지 않음)에 `sellerRevenueCacheCustomizer().customize(builder)`를 적용한 뒤, `builder.getCacheConfigurationFor(SELLER_REVENUE_CACHE)`로 얻은 `RedisCacheConfiguration`의 `getValueSerializationPair()`로 non-serializable `RevenueResponse` 인스턴스를 실제로 write(byte 직렬화) → read(역직렬화) 왕복시켜 `assertEquals(original, deserialized)`로 검증한다. 실제 Redis 서버 연결 없이(로컬에 Redis 미설치 상태 그대로) 직렬화기 설정 자체를 검증하는 방식.
  - `./gradlew compileJava` 성공(경고 없음, `GenericJackson2JsonRedisSerializer` 대비). `./gradlew test --tests "com.gong9ri.gong9ri.config.CacheConfigTest"` 성공(1건 통과) — MySQL/Redis 둘 다 로컬 미가동 상태(`Test-NetConnection` 포트 3306/6379 둘 다 `False` 재확인)에서도 이 신규 테스트는 순수 단위 테스트라 영향받지 않고 통과함을 확인.
  - 기존 `SellerMypageControllerTest`/`SellerRevenueCachingTest`(`@SpringBootTest` 기반) 재실행은 Attempt 1과 동일하게 로컬 MySQL 미가동으로 이번 단계에서 수행하지 않았다(Evaluate 단계 몫).

- 결과: ✅ PASS (로직/컨벤션 검증 기준. `@SpringBootTest` 계열 통합 테스트는 DB 미가동으로 이번에도 미실행 — 아래 참고)
  - **계산적 평가**:
    - `./gradlew compileJava compileTestJava` → 성공 (`--rerun`으로 강제 재컴파일해 확인. `GenericJackson2JsonRedisSerializer` 관련 `[removal]` 경고 없음 — Attempt 2 보고와 일치).
    - `./gradlew test --tests "com.gong9ri.gong9ri.config.CacheConfigTest" --rerun` → 성공. `build/test-results/test/TEST-com.gong9ri.gong9ri.config.CacheConfigTest.xml` 확인: `tests="1" failures="0" errors="0"`. `LettuceConnectionFactory`는 생성자 호출만(연결 시도 없음)이라 실제 Redis 서버 없이도 통과함을 재확인.
    - `Test-NetConnection -ComputerName localhost -Port 3306`/`-Port 6379` → 둘 다 `TcpTestSucceeded: False` (여전히 미가동). 따라서 `SellerMypageControllerTest`(11케이스)·`SellerRevenueCachingTest`(4케이스) 등 `@SpringBootTest` 계열은 이번 Evaluate에서도 **실행하지 않았다**(강행하지 않음) — 이 결함/수정과 무관하게 인프라 제약으로 미결 상태다.
  - **추론적 평가**:
    - `CacheConfig.java` 확인: `sellerRevenueCacheCustomizer()`가 `RedisCacheConfiguration.defaultCacheConfig().entryTtl(10분).serializeValuesWith(...)`에 `new JacksonJsonRedisSerializer<>(RevenueResponse.class)`(타입 고정, "Generic" 아님)를 값 직렬화기로 명시 설정 — 보고 내용과 실제 코드 일치 확인. `withCacheConfiguration(SELLER_REVENUE_CACHE, ...)`로 `sellerRevenue` 캐시 이름에만 스코핑돼 있어, 이 캐시가 `RevenueResponse` 단일 타입만 값으로 다루는 현재 스코프엔 적합하다. 단, 이 설정은 전역 디폴트가 아니라 캐시 이름별 오버라이드이므로, 추후 다른 `@Cacheable` 캐시가 추가되면 각자 자기 타입에 맞는 직렬화기를 별도로 등록해야 한다(자동으로 이 설정을 상속하지 않음) — 확장 시 유의점으로만 기록, 현재 스코프에선 결함 아님.
    - `CacheConfigTest.java` 확인: `@SpringBootTest` 없는 순수 단위 테스트. `RedisCacheManager.RedisCacheManagerBuilder`에 실제 커스터마이저를 적용해 얻은 `RedisCacheConfiguration.getValueSerializationPair()`로 non-serializable `RevenueResponse` 인스턴스를 실제 write(byte 직렬화)→read(역직렬화) 왕복시키고 `assertEquals(original, deserialized)`로 검증 — 형식적 테스트가 아니라 Attempt 2 도중 실제로 발견된 결함(`GenericJacksonJsonRedisSerializer` 사용 시 역직렬화 결과가 `LinkedHashMap`으로 나와 `assertEquals`가 실패하는 것)을 잡아낼 수 있는 실질적 회귀 테스트로 판단.
    - `docs/policy/caching.md` 재확인: 캐싱 로직이 `SellerMypageService`(`@Cacheable`)·`PaymentService`·`TeamDeadlineService`(`CacheManager.evict`) Service 계층에만 있고 Controller·Repository는 미개입 — 계층 제약 준수. TTL(10분) 안전장치 유지. 무효화 시점(결제 발생·환불 처리)도 정책과 일치.
    - `docs/code-convention.md` 재확인: 생성자 주입(`@RequiredArgsConstructor`) 유지, `@Transactional(readOnly = true)` 기본 + 쓰기 메서드만 `@Transactional` 패턴 유지, 매직 넘버 없음(TTL 상수화), 로깅은 기존 SLF4J 패턴 그대로(이번 변경으로 로깅 추가/변경 없음) — 위반 없음.
    - `git diff --stat` 및 `PaymentService.java`/`SellerMypageService.java`/`TeamDeadlineService.java` 재확인: Attempt 1에서 보고된 내용(캐시 무효화 트리거 위치·조건)과 동일, Attempt 2에서 추가로 손댄 흔적 없음(변경분은 `CacheConfig.java`와 신규 `CacheConfigTest.java`로 국한). Attempt 1 결함(값 직렬화기 미설정)만 해소됐다.
  - 종합 판정: **로직/컨벤션 통과**. 단, `SellerMypageControllerTest`/`SellerRevenueCachingTest` 등 `@SpringBootTest` 통합 테스트는 로컬 MySQL/Redis 미가동으로 Attempt 1·2·Evaluate 전 과정에서 한 번도 실제 실행되지 못했다 — 캐시 히트·무효화(결제/환불) 시나리오의 **엔드투엔드 동작은 여전히 미검증** 상태이며, 이는 이번 판정의 범위 밖(인프라 가동 후 별도 확인 필요)이다.
- 원인: (해당 없음 — 이번 시도로 결함 없음. Attempt 1 원인은 위 Attempt 1 절 참고)
- 증거:
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.config.CacheConfigTest.xml`: `<testsuite ... tests="1" skipped="0" failures="0" errors="0" .../>`
  - `./gradlew compileJava --rerun` 출력: `BUILD SUCCESSFUL`, 경고 없음.
  - `Test-NetConnection -ComputerName localhost -Port 3306` / `-Port 6379`: 둘 다 `TcpTestSucceeded : False` (2026-08-05 기준).
- 다음: Evaluate 통과 — `docs/dev/mypage/view/design.md` 갱신 + `docs/dev/ongoing/mypage-seller-revenue-caching.md`를 `docs/dev/mypage/view/changes/002-caching.md`로 채번 이동. `@SpringBootTest` 계열(`SellerMypageControllerTest`, `SellerRevenueCachingTest`) 회귀 확인은 MySQL/Redis 가동 가능해지는 시점에 별도로 재검증 필요(이번 판정에 포함되지 않음).

## Attempt 3 — 2026-08-05  ✅ PASS (실제 MySQL/Redis 인프라로 최초 엔드투엔드 검증)

- 시도: 사용자 요청 두 가지를 반영했다 — (1) 캐시 이용 전/후 결과값을 명시적으로 비교해 "진짜로 캐시가 관여했는지" 증명할 것, (2) 더미 데이터를 유의미한 규모로 쓸 것. 이를 위해 `SellerRevenueCachingTest.java`를 다시 작성했다.
  - `seedDummyPayments(buyer, product, paidCount, refundedCount)` 헬퍼 추가: PAID 25건(1000원 단위 증가 금액, 합계를 손으로도 검증 가능하게 구성)과 REFUNDED 5건을 대량으로 채워, 집계값이 우연히 맞아떨어지지 않는 규모로 만들었다.
  - 캐시 히트 테스트를 강화: 기존엔 "반복 조회 시 레포지토리가 한 번만 불렸다"는 것만 확인했는데, 이것만으론 "데이터가 안 바뀌어서 우연히 같았다"와 "진짜로 캐시가 이전 값을 들고 있다"를 구분 못 한다. 그래서 첫 조회 후 **캐시 무효화 경로(PaymentService.create/TeamDeadlineService.processDeadline)를 거치지 않고 레포지토리에 직접 결제(999,000원)를 꽂아 넣어** 실제 DB 데이터를 바꾼 뒤 재조회했고, 재조회 값이 이 변경을 반영하지 않고 첫 조회 값과 완전히 동일함(`assertEquals(first, second)` + `assertNotEquals(expectedTotal + 999_000, second.totalRevenue())`)을 확인해 "이건 매번 새로 쿼리한 게 아니라 캐시에서 나온 값"임을 직접 증명하는 구조로 바꿨다.
  - 결제 생성/환불 무효화 테스트도 대량 베이스라인(기존 결제 25+5건) 위에 새 결제/환불을 얹어, `before`/`after` 값이 정확히 어떤 차이(정확한 금액·건수 delta)를 보이는지 계산해서 검증하고 `assertNotEquals(before, after)`를 명시적으로 추가했다. 환불 테스트는 대상 팀과 무관한 기존 결제(베이스라인)가 그대로 남아있는지도 같이 확인해 "팀 단위로만 정확히" 무효화/차감되는지 구분했다.
  - 이와 별개로, 사용자가 로컬에 Docker Desktop을 설치 → WSL2 활성화까지 진행해줘서, 이번이 **최초로 실제 MySQL(Docker `mysql:8`, 포트 3306)과 실제 Redis(Docker `redis:7`, 포트 6379)가 가동된 상태에서의 검증**이 됐다. (Docker Desktop이 "Inference"/Docker AI 기능에서 사용자 홈 경로의 비-ASCII 문자 때문에 백엔드가 크래시 루프를 도는 별개 이슈가 있었는데, `%APPDATA%\Docker\settings-store.json`의 `EnableDockerAI`를 `false`로 꺼서 우회함 — 이 캐싱 기능 자체와는 무관한 로컬 환경 이슈.)
- 결과:
  - `./gradlew test --tests "*SellerMypage*" --tests "*SellerRevenueCaching*" --tests "*CacheConfig*"` → **BUILD SUCCESSFUL**, 전부 통과: `SellerMypageControllerTest` 11/11, `SellerRevenueCachingTest`(재작성판) 4/4, `CacheConfigTest` 1/1 (`tests`/`failures`/`errors` XML로 확인).
  - `./gradlew build`(전체 스위트, CI와 동일) → **BUILD SUCCESSFUL**. 저장소 전체 테스트 결과 XML 합산: `tests=75, skipped=0, failures=0, errors=0`.
  - Attempt 1·2에서 "DB 미가동으로 미결"이라 남겨뒀던 `@SpringBootTest` 계열 캐시 히트·무효화 엔드투엔드 시나리오가 **이번에 처음으로 실제 통과 확인됨**.
- 원인: (해당 없음 — 실패 없음)
- 증거:
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.controller.SellerMypageControllerTest.xml`: `tests="11" failures="0" errors="0"`
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.service.SellerRevenueCachingTest.xml`: `tests="4" failures="0" errors="0"`
  - `build/test-results/test/TEST-com.gong9ri.gong9ri.config.CacheConfigTest.xml`: `tests="1" failures="0" errors="0"`
  - 저장소 전체 `build/test-results/test/*.xml` 합산: `tests=75 skipped=0 failures=0 errors=0`
  - `docker ps`: `gong9ri-mysql`(mysql:8, 3306), `gong9ri-redis`(redis:7, 6379) 정상 기동 확인.
- 다음: 없음 — 이번 라운드로 캐싱 기능은 실제 MySQL/Redis 환경에서까지 종합적으로 검증 완료. `docs/dev/mypage/view/design.md`/`changes/002-caching.md`는 Attempt 2 시점 상태 그대로 유효(이번 라운드는 테스트 품질 보강 + 실제 인프라 검증이며 캐싱 동작 자체의 설계 변경은 없었음). 로컬에 띄운 `gong9ri-mysql`/`gong9ri-redis` 컨테이너는 팀 공용 CI 설정과 무관한 개인 검증용이라 필요 없어지면 정리(`docker rm -f`) 대상.
