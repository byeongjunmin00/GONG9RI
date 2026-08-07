# 001-migration — 문자열 JPQL → QueryDSL(Jakarta) 전환 (로그)

## Attempt 1 — 2026-08-07

- 시도: `docs/dev/ongoing/querydsl-migration.md`에 승인된 계획대로, `build.gradle`에 QueryDSL(Jakarta)
  의존성·Q타입 생성 설정을 추가하고, 대상 6개 리포지토리(ProductRepository, PriceTierRepository,
  PaymentRepository, TeamParticipationRepository, GroupBuyTeamRepository,
  SellerRevenueSummaryRepository의 `applyRefund`만)를 `JpaRepository<T,ID> + XxxRepositoryCustom`
  합성 패턴으로 전환했다.

### QueryDSL 아티팩트/버전 선택

- 이 저장소는 Spring Boot 4.1.0 + Hibernate ORM 7.4.1.Final + `jakarta.persistence-api:3.2.0`을 쓴다
  (`./gradlew dependencies --configuration compileClasspath`로 확인). QueryDSL은 최근 5.x대에서 정식
  릴리스가 멈춘 상태라 이 조합과의 호환성이 검증된 적이 없어, 실제로 빌드해서 확인하는 것 외에는
  방법이 없었다.
- `com.querydsl:querydsl-jpa:5.1.0:jakarta` (jakarta classifier) + annotationProcessor로
  `com.querydsl:querydsl-apt:5.1.0:jakarta` + `jakarta.persistence:jakarta.persistence-api` +
  `jakarta.annotation:jakarta.annotation-api`를 추가하고 `./gradlew compileJava`를 돌려본 결과,
  **첫 시도에서 바로 성공**했다 — `build/generated/querydsl/.../Q*.java` 7개(Product, PriceTier,
  Payment, TeamParticipation, GroupBuyTeam, SellerRevenueSummary, Member)가 정상 생성되고 컴파일도
  통과했다. 별도 버전을 더 시도해볼 필요가 없었다 — QueryDSL 5.1.0의 APT 프로세서가 생성하는 코드가
  jakarta.persistence 애노테이션(`@Entity`, `@ManyToOne` 등)의 표면적 API에만 의존해서, persistence-api
  3.2.0으로 올라간 것과 무관하게 소스 호환됐던 것으로 보인다(Hibernate 7 내부 구현과는 무관 — QueryDSL은
  APT 단계에서 엔티티 클래스만 스캔하고, 런타임에는 표준 `EntityManager`/JPQL만 사용하기 때문).
- Q타입 생성 소스를 컴파일 소스셋에 포함시키기 위해 `build.gradle`에 다음을 추가했다:
  - `sourceSets.main.java.srcDirs += build/generated/querydsl`
  - `tasks.withType(JavaCompile).configureEach { options.generatedSourceOutputDirectory.set(...) }`
  - `tasks.named('clean') { delete querydslDir }` (clean 시 생성물도 같이 지움)

### 커스텀 리포지토리 패턴 적용 방식

- 각 리포지토리마다 `XxxRepositoryCustom`(인터페이스, QueryDSL 전환 대상 메서드만) +
  `XxxRepositoryImpl`(클래스, Spring Data가 `Xxx` + `Impl` 네이밍 규칙으로 자동 감지) +
  기존 `XxxRepository extends JpaRepository<T,ID>, XxxRepositoryCustom`으로 합성했다.
  파생 쿼리(`existsByX`, `deleteByX`, `findByProductIdAndStatus` 등)는 원래 JPQL 문자열이 아니므로
  그대로 `XxxRepository`에 남겨뒀다(계획 문서의 "파생 메서드는 전환 대상 아님"과 일치).
- `XxxRepositoryImpl` 생성자는 `EntityManager`를 받아 내부에서 `new JPAQueryFactory(entityManager)`를
  만든다(Spring Data JPA가 커스텀 구현체 인스턴스화 시 `EntityManager`를 생성자 인자로 주입해주는
  표준 패턴 — 별도 `@Configuration`에 `JPAQueryFactory` 빈을 두지 않아 계획의 "build.gradle과
  repository 계층만 건드린다" 범위를 지켰다).
- 서비스 계층 호출부는 전혀 건드리지 않았다 — 모든 인터페이스 메서드의 이름·파라미터·리턴타입을
  그대로 유지했다.

### 리포지토리별 전환 내용

- **ProductRepository**: `findAllWithSeller`(페이징+페치조인, count는 별도 select), `findByIdWithSeller`
  (페치조인+단건). `findAllWithSeller`는 `Pageable.getSort()`가 있으면 `PathBuilder` 기반으로 동적
  `OrderSpecifier`를 만들어 정렬을 반영하되(원래 정렬 없음이면 `ORDER BY` 없이 원래 동작과 동일하게
  유지), 실제 호출부(`ProductService.list`)는 항상 `PageRequest.of(page, size)`(정렬 없음)라 이
  분기는 현재 테스트로 직접 검증되진 않는다 — 향후 정렬 파라미터가 생기면 대비용.
- **PriceTierRepository**: `findBestPricesByProductIds` — `GROUP BY` + `MIN(price)` 집계.
  QueryDSL은 인터페이스 프로젝션(bean binding)을 지원하지 않아, `BestPriceProjection`을 구현하는
  구체 클래스 `BestPriceProjectionImpl`을 새로 만들어 `Projections.constructor`로 바인딩했다.
  (`RevenueSummaryProjection`도 동일한 이유로 `RevenueSummaryProjectionImpl` 추가.)
- **PaymentRepository**: `findByIdWithDetails`(다중 페치조인, team은 nullable이라 leftJoin),
  `findAllByMemberIdWithProduct`(페치조인+정렬), `findRevenueSummaryBySellerId`(CASE WHEN + SUM +
  COALESCE 집계 — QueryDSL `CaseBuilder` + `NumberExpression.coalesce`로 표현),
  `findDistinctSellerIdsWithPayments`(distinct select).
- **TeamParticipationRepository**: `findAllByMemberIdWithTeamAndProduct` — 2단 페치조인
  (`teamParticipation.team`, `teamParticipation.team.product`).
- **GroupBuyTeamRepository**(동시성 핵심 로직, 특히 신경 씀):
  - `findByIdForUpdate` — `JPAQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE)`로 원래와 동일한
    락 모드 유지.
  - `findAllBySellerIdWithProduct` — 페치조인+정렬.
  - `findIdsByStatusAndDeadlineBefore` — id만 select.
  - `incrementIfCapacity` — `JPAUpdateClause`로 원자적 벌크 UPDATE. `currentCount = currentCount + 1`,
    `status`는 `CaseBuilder`로 `currentCount+1 == maxParticipants`일 때만 SUCCESS로 CASE 분기,
    `updatedAt`은 `DateTimeExpression.currentTimestamp(LocalDateTime.class)`로 원래 JPQL의
    `CURRENT_TIMESTAMP`(DB 함수 호출)와 동일하게 렌더링되도록 함(자바 애플리케이션 시각을 박아넣는
    방식이 아님). 원래 `@Modifying(clearAutomatically = true)`와 동일하게 `execute()` 후
    `entityManager.clear()`를 수동으로 호출해 컨텍스트를 비웠다(flushAutomatically는 원래도 없었으므로
    추가하지 않음 — Hibernate의 기본 FlushMode.AUTO가 벌크 UPDATE 실행 전 자동 플러시하는 동작은
    QueryDSL `.execute()`도 동일하게 상속받는다, 원래 JPQL `@Modifying` 방식과 다르지 않음).
- **SellerRevenueSummaryRepository**: `applyRefund`만 전환(계획대로 `incrementPaid`는 네이티브
  upsert 그대로 유지). 원래 `@Modifying(flushAutomatically = true, clearAutomatically = true)`와
  동일한 순서(먼저 `entityManager.flush()` → 벌크 UPDATE 실행 → `entityManager.clear()`)를 그대로
  재현했다 — 주석에 남아있던 "TeamDeadlineService가 결제 엔티티를 더티로 만든 직후 이 호출이 flush를
  안 하면 유실된다"는 경고를 그대로 지켰다.

### 겪은 문제와 해결

1. QueryDSL 버전/Jakarta 호환성 — 예상과 달리 문제 없이 한 번에 컴파일됨(위 설명 참고).
2. `@DataJpaTest`/`@AutoConfigureTestDatabase` 임포트 경로 — Spring Boot 4.1.0에서 테스트 슬라이스
   애노테이션 패키지가 모듈별로 재편되어 기존에 익숙한
   `org.springframework.boot.test.autoconfigure.orm.jpa.*` 경로가 더 이상 없었다. 실제 의존성 jar를
   풀어서 확인한 뒤 `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`,
   `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`로 고쳐서 해결했다
   (`spring-boot-starter-data-jpa-test`가 `spring-boot-jdbc-test`를 전이 의존성으로 이미 포함하고
   있어 별도 의존성 추가는 필요 없었다).
3. 로컬에 실행 중인 MySQL이 없어(`Test-NetConnection 127.0.0.1:3306` 실패, Docker Desktop도 정지
   상태) 처음엔 테스트를 못 돌렸다. `C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe`가 이미
   설치돼 있고 `C:\ProgramData\MySQL\MySQL Server 8.4\Data`에 기존 데이터 디렉터리가 있는 것을 확인해
   (이전에 이 프로젝트 로컬 개발용으로 초기화된 것으로 보임), 그 mysqld를 직접 실행해 기동한 뒤 테스트를
   돌렸다.

### 서비스 레벨 테스트 커버리지 확인 (태스크 6번째 항목)

- 계획 문서가 지정한 6개 테스트(`TeamConcurrencyTest`, `TeamConcurrencyAtomicTest`,
  `SellerRevenueSummaryTest`, `SellerRevenueSummaryConcurrencyTest`, `TeamDeadlineServiceTest`,
  `ProductCachingTest`) 전부와 컨트롤러 테스트들(`ProductControllerTest`, `PaymentControllerTest`,
  `BuyerMypageControllerTest`, `SellerMypageControllerTest` 등)을 하나씩 대조해, 전환된 11개
  커스텀 쿼리 메서드 중 `PaymentRepository.findDistinctSellerIdsWithPayments()`만 어떤 서비스
  테스트에서도 실제로 호출되지 않는 것을 확인했다(호출부인
  `SellerRevenueSummaryBackfillService.backfillMissingSummaries()` 자체를 부르는 테스트가 없고,
  기존 테스트는 `backfillOneIfMissing(특정 sellerId)`만 직접 호출함). 나머지 10개는 모두 기존
  서비스/컨트롤러 테스트가 실제 DB를 상대로 이미 커버하고 있었다(예: `ProductCachingTest`가
  `@MockitoSpyBean`으로 `findAllWithSeller`/`findByIdWithSeller`를 정확한 호출 횟수까지 검증,
  `ProductControllerTest.list_publicAccess`가 `findBestPricesByProductIds`의 결과값을 검증).
- 커버되지 않는 그 한 메서드에 대해 `@DataJpaTest`(+ `@AutoConfigureTestDatabase(replace = NONE)`,
  이 저장소엔 임베디드 DB가 없어 실제 MySQL을 그대로 씀) 슬라이스 테스트
  `src/test/java/com/gong9ri/gong9ri/repository/PaymentRepositoryTest.java`를 새로 추가해,
  결제 이력이 있는 판매자 id가 중복 없이 나오는지·결제 이력이 없는 판매자는 빠지는지 2케이스로
  검증했다.

### 실행 결과

- `./gradlew compileJava` — 성공(Q타입 7개 생성 확인).
- `./gradlew test`(로컬 MySQL 기동 후 전체) — **15개 테스트 클래스, 87개 테스트 전부 통과, 실패/에러
  0건**(`build/test-results/test/*.xml` 집계 기준). 태스크에 지정된 6개 테스트 클래스 + 신규
  `PaymentRepositoryTest` 포함.
- 변경 파일: `build.gradle`(수정), `repository/` 패키지의 기존 6개 인터페이스(수정) + 신규
  `*RepositoryCustom.java`(6개) + `*RepositoryImpl.java`(6개) + `BestPriceProjectionImpl.java` +
  `RevenueSummaryProjectionImpl.java`, `src/test/java/com/gong9ri/gong9ri/repository/
  PaymentRepositoryTest.java`(신규). entity/dto/service/controller 계층은 손대지 않았다.

## 평가 — 2026-08-07  ✅ PASS

- 결과: **계산적 평가·추론적 평가 모두 통과.**
- 계산적 평가:
  - 로컬 MySQL이 안 떠 있어(`Test-NetConnection 127.0.0.1 -Port 3306` → `False`) 직접
    `mysqld.exe --datadir="C:\ProgramData\MySQL\MySQL Server 8.4\Data"`로 기동한 뒤 재확인
    (`True`).
  - `./gradlew clean compileJava` — **성공**(clean 상태에서 재생성해도 Q타입 7개
    `build/generated/querydsl/com/gong9ri/gong9ri/entity/Q*.java`가 재생성되고 컴파일 통과, 캐시
    의존 아님을 확인).
  - `./gradlew test`(전체) — **BUILD SUCCESSFUL**. `build/test-results/test/*.xml` 15개 파일을
    직접 집계한 결과 **15개 테스트 클래스, 87개 테스트, failures=0, errors=0** — generator가
    보고한 "87개 전부 통과"와 일치(재확인 완료, 그대로 믿지 않고 직접 재실행·재집계함).
  - 평가기준에 명시된 6개 테스트 클래스(`TeamConcurrencyTest`, `TeamConcurrencyAtomicTest`,
    `SellerRevenueSummaryTest`, `SellerRevenueSummaryConcurrencyTest`, `TeamDeadlineServiceTest`,
    `ProductCachingTest`) + 신규 `PaymentRepositoryTest` 전부 xml 집계에 포함되어 있고 개별
    failures/errors 0건 확인.
- 추론적 평가:
  - **범위 준수**: `git diff --stat` 기준 변경 파일이 `build.gradle` + `repository/` 패키지(기존
    인터페이스 6개 수정 + `*Custom`/`*Impl` 신규 12개 + Projection 구현체 2개) + 신규 테스트
    1개뿐. entity/dto/service/controller 계층 변경 없음 확인.
  - **시그니처 불변**: `ProductRepository`/`PriceTierRepository`/`PaymentRepository`/
    `TeamParticipationRepository`/`GroupBuyTeamRepository`/`SellerRevenueSummaryRepository` 전부
    `git diff`로 대조 — 전환 대상 메서드가 인터페이스에서 빠지고 `XxxRepositoryCustom`으로
    옮겨졌을 뿐, 각 `XxxRepositoryCustom`의 메서드명·파라미터·리턴타입이 원래 `@Query` 메서드와
    동일함을 코드로 직접 대조 확인. 파생 메서드(`findByTeamIdAndStatus` 등)는 그대로 원본
    인터페이스에 남아있음.
  - **`incrementPaid` 제외 확인**: `SellerRevenueSummaryRepository.java`를 직접 읽어, 계획대로
    native `@Query(nativeQuery = true)` + `ON DUPLICATE KEY UPDATE` upsert가 그대로 남아있고
    `SellerRevenueSummaryRepositoryCustom`에는 `applyRefund`만 있음을 확인.
  - **동시성 핵심 로직 회귀 없음**: `GroupBuyTeamRepositoryImpl`을 직접 읽어 확인 —
    `findByIdForUpdate`는 `JPAQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE)`로 원래 락 모드
    그대로. `incrementIfCapacity`는 `where(id.eq(id), currentCount.lt(maxParticipants))` 조건부
    벌크 UPDATE(원자적 정원 체크 유지) + `CaseBuilder`로 `currentCount+1 == maxParticipants`일 때만
    SUCCESS 전환 + `entityManager.clear()`(원래 `clearAutomatically = true`와 동일)까지 원본
    JPQL 버전과 동일한 동작.
  - **코드 컨벤션 준수**: 각 `XxxRepositoryImpl`이 `EntityManager` 생성자 주입으로
    `JPAQueryFactory`를 구성(필드 `@Autowired` 없음, `final` 필드), `repository` 계층만 건드려
    계층 분리 유지. 신규 테스트는 `@Autowired` 필드 주입을 쓰지만 이는 테스트 클래스이고
    code-convention의 생성자 주입 규칙은 서비스 계층 대상이라 해당 없음.
  - **범위 확장 없음**: 계획에 없던 리팩토링(예: entity/dto 변경, 불필요한 API 변경) 발견되지
    않음.
- 원인: 해당 없음(PASS).
- 증거: `./gradlew test` → `BUILD SUCCESSFUL in 20s`, `build/test-results/test/*.xml` 집계
  `tests=87 failures=0 errors=0`(클래스 15개, 예: `TeamConcurrencyAtomicTest tests="1"
  failures="0" errors="0"`, `PaymentRepositoryTest tests="2" failures="0" errors="0"`).
