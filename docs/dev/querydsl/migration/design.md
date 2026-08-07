# 타입 안전 쿼리(QueryDSL) 도입 (querydsl/migration) — Design

## 개요

리포지토리 레이어의 문자열 기반 JPQL `@Query`를 QueryDSL(Jakarta, `JPAQueryFactory`) 기반 커스텀
리포지토리로 전환해, 필드명·문법 오류를 컴파일 타임에 잡는다. 호출부(service) 영향을 없애기 위해
전환된 리포지토리 인터페이스의 **public 메서드 시그니처(이름·파라미터·리턴타입)는 전환 전과 동일**하게
유지한다 — QueryDSL은 인터페이스 뒤에 숨겨진 구현 세부사항일 뿐이다.

### 현재 전환 현황 (2026-08-07 기준)

| 리포지토리 | 상태 | 비고 |
|---|---|---|
| `ProductRepository` | QueryDSL 전환 완료 | `findAllWithSeller`(페이징+페치조인, count 별도 select), `findByIdWithSeller`(페치조인+단건). 파생 메서드(`findAllBySellerIdOrderByCreatedAtDesc`)는 전환 대상 아님(원래도 JPQL 문자열이 아님) |
| `PriceTierRepository` | QueryDSL 전환 완료 | `findBestPricesByProductIds`(GROUP BY + MIN 집계, `BestPriceProjectionImpl`로 프로젝션). 파생 메서드(`findByProductIdOrderByMinCountAsc`, `deleteByProductId`)는 전환 대상 아님 |
| `PaymentRepository` | QueryDSL 전환 완료 | `findByIdWithDetails`(다중 페치조인, team은 leftJoin), `findAllByMemberIdWithProduct`(페치조인+정렬), `findRevenueSummaryBySellerId`(CASE+SUM+COALESCE 집계, `RevenueSummaryProjectionImpl`), `findDistinctSellerIdsWithPayments`(distinct). 파생 메서드(`findByTeamIdAndStatus`)는 전환 대상 아님 |
| `TeamParticipationRepository` | QueryDSL 전환 완료 | `findAllByMemberIdWithTeamAndProduct`(2단 페치조인). 파생 메서드(`existsByTeamIdAndMemberId`, `deleteByTeamId`)는 전환 대상 아님 |
| `GroupBuyTeamRepository` | QueryDSL 전환 완료 | `findByIdForUpdate`(비관적 락, `PESSIMISTIC_WRITE` 유지), `findAllBySellerIdWithProduct`(페치조인+정렬), `findIdsByStatusAndDeadlineBefore`(id만 select), `incrementIfCapacity`(원자적 벌크 UPDATE, `CaseBuilder`로 상태 CASE 분기 + 조건부 정원 체크). 파생 메서드(`findByProductIdAndStatus`)는 전환 대상 아님 |
| `SellerRevenueSummaryRepository` | 부분 전환 | `applyRefund`(조건부 벌크 UPDATE)만 QueryDSL로 전환. **`incrementPaid`는 QueryDSL 미지원 영역(native `INSERT ... ON DUPLICATE KEY UPDATE` upsert)이라 전환 대상에서 제외하고 문자열 `@Query(nativeQuery = true)` 그대로 유지**(향후에도 QueryDSL로 옮길 계획 없음 — QueryDSL은 표준 JPQL/CriteriaAPI 위에서 동작해 upsert 문법을 표현할 수 없음) |
| `MemberRepository` | 전환 대상 아님 | 이번 작업 시작 시점에 `@Query` 문자열 사용이 없어(전부 파생 메서드) 손대지 않음 |

## API / 인터페이스

- 대상 기능이 아니라 리포지토리 계층 내부 구현 방식 전환이라 REST 엔드포인트 변화 없음 — 상세 API는
  각 기능의 `docs/api/*.md` 참고.

## 데이터 모델

- 신규 테이블·컬럼 없음. 기존 엔티티(`Product`, `PriceTier`, `Payment`, `TeamParticipation`,
  `GroupBuyTeam`, `SellerRevenueSummary`, `Member`) 위에서 조회 방식만 바뀜.
- Q타입(`build/generated/querydsl/com/gong9ri/gong9ri/entity/Q*.java`)은 위 7개 엔티티에 대해
  애노테이션 프로세서(`querydsl-apt:5.1.0:jakarta`)가 빌드마다 자동 생성한다(git에 커밋되지 않음,
  `clean` 시 함께 삭제됨).

## 규칙 / 검증

### 새 리포지토리를 추가/확장할 때 (패턴)

- 리포지토리에 **커스텀 쿼리(JPQL이었던 것)가 필요하면** 다음 3개 파일 세트로 만든다:
  1. `XxxRepositoryCustom`(인터페이스) — 커스텀 메서드 시그니처만 선언.
  2. `XxxRepositoryImpl`(클래스) — `XxxRepositoryCustom` 구현. 생성자로 `EntityManager`를 받아
     내부에서 `new JPAQueryFactory(entityManager)`를 만든다(별도 `@Configuration` 빈 불필요 —
     Spring Data JPA가 `Xxx` + `Impl` 네이밍 규칙으로 자동 감지해 생성자에 `EntityManager`를
     주입해준다).
  3. `XxxRepository extends JpaRepository<T, ID>, XxxRepositoryCustom` — 최종 인터페이스, 서비스
     계층은 이 하나만 주입받는다.
- **파생 쿼리 메서드**(`findByX`, `existsByX`, `deleteByX` 등)는 원래 문자열 JPQL이 아니므로
  QueryDSL로 옮기지 않는다 — `XxxRepository`에 그대로 둔다.
- **집계/프로젝션이 필요하면** QueryDSL이 인터페이스 프로젝션(bean binding)을 지원하지 않으므로,
  프로젝션 인터페이스를 구현하는 구체 클래스(`XxxProjectionImpl`)를 만들고
  `Projections.constructor(XxxProjectionImpl.class, ...)`로 바인딩한다(예:
  `BestPriceProjectionImpl`, `RevenueSummaryProjectionImpl`).
- **비관적 락**은 `JPAQuery.setLockMode(LockModeType.XXX)`로 표현한다(원래 `@Lock` 애노테이션과
  동일 락 모드 유지가 필수 — 동시성 핵심 로직).
- **벌크 UPDATE**는 `JPAUpdateClause`(`queryFactory.update(qEntity)...`)로 표현하고, 원래
  `@Modifying(flushAutomatically, clearAutomatically)`가 하던 것과 동일하게 필요 시
  `entityManager.flush()`(실행 전)·`entityManager.clear()`(실행 후)를 수동으로 호출한다 — QueryDSL의
  `.execute()`는 JPQL `@Modifying`과 달리 flush/clear를 자동으로 해주지 않는다.
- **CASE 표현식**은 `CaseBuilder`, **DB 함수 `CURRENT_TIMESTAMP`**는
  `DateTimeExpression.currentTimestamp(LocalDateTime.class)`로 표현한다(애플리케이션 시각을
  박아넣지 않고 DB 함수 호출을 그대로 유지하기 위함).
- **Native SQL(upsert 등 QueryDSL이 표현 못 하는 영역)은 전환하지 않는다** — 기존 문자열
  `@Query(nativeQuery = true)`를 그대로 둔다(`SellerRevenueSummaryRepository.incrementPaid`가
  선례).
- QueryDSL 아티팩트: `com.querydsl:querydsl-jpa:5.1.0:jakarta` +
  `annotationProcessor 'com.querydsl:querydsl-apt:5.1.0:jakarta'`(Spring Boot 4.1.0 / Hibernate
  ORM 7.4.1.Final / `jakarta.persistence-api:3.2.0` 조합에서 호환 확인됨, `build.gradle` 참고).

## 관련 코드 위치

- `build.gradle` — QueryDSL 의존성 + Q타입 생성 소스셋 설정(`sourceSets.main.java.srcDirs`,
  `JavaCompile.options.generatedSourceOutputDirectory`, `clean` 시 생성물 삭제).
- `repository/{Product,PriceTier,Payment,TeamParticipation,GroupBuyTeam,SellerRevenueSummary}Repository.java`
  — 커스텀 인터페이스 합성으로 변경(메서드 시그니처는 전환 전과 동일, 위 표 참고).
- `repository/{Product,PriceTier,Payment,TeamParticipation,GroupBuyTeam,SellerRevenueSummary}RepositoryCustom.java`,
  `repository/{...}RepositoryImpl.java` — 신규, QueryDSL 구현.
- `repository/{BestPrice,RevenueSummary}ProjectionImpl.java` — 신규, 집계 프로젝션 구현체.
- `src/test/java/com/gong9ri/gong9ri/repository/PaymentRepositoryTest.java` — 신규,
  `@DataJpaTest`(+ `@AutoConfigureTestDatabase(replace = NONE)`, 임베디드 DB 없어 로컬 MySQL 사용)
  슬라이스 테스트. 서비스 레벨 테스트로 커버되지 않던 `findDistinctSellerIdsWithPayments()` 전용.
- 이번 전환은 `entity`/`dto`/`service`/`controller` 계층을 건드리지 않았다(호출부 시그니처 불변).
- 경위: `docs/dev/querydsl/migration/changes/001-migration.md`, 실행 로그:
  `docs/logs/querydsl/migration/001-migration.md`.
