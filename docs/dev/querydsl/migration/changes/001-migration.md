# 타입 안전 쿼리(QueryDSL) 도입

대상: querydsl/migration
담당: 전용운

## 배경 / 요구

- 현재 모든 리포지토리가 문자열 기반 JPQL `@Query`로 작성되어 있어 컴파일 타임에 필드명·문법 오류를 잡지 못한다(발제 필수 검토 항목).
- 리포지토리가 7개(그중 `@Query` 사용 6개)뿐인 지금이 이후 리포지토리·쿼리가 늘어난 뒤보다 마이그레이션 비용이 가장 낮은 시점이다.

## 설계

- **대상**: JPQL 기반 조회·페치조인·비관적 락·JPQL 벌크 `UPDATE`는 QueryDSL(Jakarta) `JPAQueryFactory` 기반 커스텀 리포지토리로 전환한다. 기존 리포지토리 인터페이스의 메서드 시그니처는 유지해 호출부(service)는 변경하지 않는다.
- **제외**: Native SQL(`SellerRevenueSummaryRepository.incrementPaid`, `ON DUPLICATE KEY UPDATE` upsert)은 QueryDSL 미지원 영역이라 이번 전환 대상에서 제외하고 기존 방식을 유지한다(리스크로만 문서화).
- **순서**: 리포지토리 단위로 하나씩 전환 후 그때그때 테스트로 확인한다.
  1. `ProductRepository`, `PriceTierRepository` (단순 조회·페이징)
  2. `PaymentRepository`, `TeamParticipationRepository` (페치조인 위주)
  3. `GroupBuyTeamRepository` (비관적 락 + CASE 포함 벌크 UPDATE — 동시성 회귀 여부 특히 확인)
  4. `SellerRevenueSummaryRepository`의 JPQL `applyRefund`만 전환 (native `incrementPaid`는 제외)
- **범위**: 이번 작업은 build.gradle(QueryDSL 의존성 + Q타입 생성 애노테이션 프로세서 설정)과 `repository` 계층만 건드린다. entity/dto/service/controller는 변경하지 않는다(호출부 시그니처 불변이 목표).

## 태스크

- [ ] build.gradle에 QueryDSL(Jakarta) 의존성 + Q타입 생성 설정 추가, 빌드로 Q타입 생성 확인
- [ ] ProductRepository, PriceTierRepository 전환
- [ ] PaymentRepository, TeamParticipationRepository 전환
- [ ] GroupBuyTeamRepository 전환 (락 쿼리 + 원자적 벌크 UPDATE 포함) — 동시성 테스트 회귀 확인
- [ ] SellerRevenueSummaryRepository의 applyRefund만 전환 (incrementPaid 제외)
- [ ] 서비스 레벨 테스트로 커버되지 않는 전환 쿼리가 있으면 `@DataJpaTest` 슬라이스 테스트 보강

## 평가(통과) 기준

- `./gradlew build` — Q타입 생성 포함 컴파일 성공
- `./gradlew test` — 기존 테스트 전체 통과(`TeamConcurrencyTest`, `TeamConcurrencyAtomicTest`, `SellerRevenueSummaryTest`, `SellerRevenueSummaryConcurrencyTest`, `TeamDeadlineServiceTest`, `ProductCachingTest` 포함), 회귀 없음
- 전환된 리포지토리의 인터페이스 메서드 시그니처가 유지되어 service/controller 코드 변경 없음

## 리스크 / 전제

- Spring Boot 4.1.0(Jakarta) ↔ QueryDSL jakarta 아티팩트 버전 호환성이 아직 검증되지 않았다 — 실제 빌드해봐야 확인 가능.
- 리포지토리 쿼리를 직접 검증하는 전용 테스트(`@DataJpaTest`)는 현재 없고, 서비스 레벨 테스트가 간접적으로 커버하고 있다 — 커버 안 되는 구간이 있을 수 있다.
- `GroupBuyTeamRepository`의 비관적 락·원자적 UPDATE는 팀 참가 동시성의 핵심 로직이라, 전환 시 회귀가 나면 영향이 크다.
- native upsert(`incrementPaid`)는 이번 전환 후에도 문자열 쿼리로 남는다(향후 별도 검토 대상).
- 2인 팀 기준 `docs/dev/ongoing/`에 현재 다른 진행 중 작업 없음(충돌 없음) 확인.
