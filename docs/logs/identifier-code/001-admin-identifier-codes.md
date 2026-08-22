# 001-admin-identifier-codes — 회원번호·상품코드·주문번호·공구팀 번호 도입 (로그)

## Attempt 1 — 2026-08-22  ❌ FAIL (Evaluate)

- 시도: 승인된 계획(`docs/dev/ongoing/admin-identifier-codes.md`)대로 4개 엔티티에 사람이 읽는
  식별 코드 컬럼을 추가하고, 신규 생성 경로 자동 채번 + 기존 데이터 백필 메커니즘 + admin/공개
  API·프론트 노출 + 관련 문서를 구현했다.

### 구현 접근

- **포맷 SSOT**: `com.gong9ri.gong9ri.common.identifier.IdentifierCodeFormatter`(정적 유틸리티,
  Spring 빈 아님) — `memberCode`/`productCode`/`teamNo`/`orderNo` 4개 메서드. PK 파생이라 별도
  카운터 테이블 없음.
- **엔티티**: `Member.memberCode`, `Product.productCode`, `Payment.orderNo`,
  `GroupBuyTeam.teamNo` 컬럼 추가 + 각각 `assignXxx(...)` 세터. **의도적으로 지금은 nullable이고
  UNIQUE 제약도 걸지 않았다** — 아래 "확정 판단" 참고.
- **채번**: 각 서비스가 저장 직후(같은 트랜잭션) 코드 할당 — `MemberService.signup()`/
  `findOrCreateByKakao()`, `ProductService.register()`, `PaymentService.create()`,
  `TeamService.create()`.
- **백필**: `IdentifierCodeBackfillService`(행 1개당 트랜잭션 분리, `SellerRevenueSummaryBackfillService`
  패턴 재사용) + `IdentifierCodeBackfillRunner`(opt-in, `app.backfill.identifier-code=true`,
  `SellerRevenueSummaryBackfillRunner` 패턴 재사용). 각 리포지토리에 `findIdsByXxxIsNull()` 추가.
- **admin 노출**: `AdminMemberResponse.memberCode` 추가. `product_code`는 공개 목록과 공유하는
  `ProductSummaryResponse`를 통해 admin 상품 현황에도 자연히 포함. 검색(`MemberRepositoryImpl`/
  `ProductRepositoryImpl` QueryDSL 조건)에 각각 `memberCode`/`productCode` `containsIgnoreCase` 추가.
- **공개 API 노출**: `ProductSummaryResponse.productCode`, `ProductResponse.productCode` 추가(기존
  필드 순서 유지 + 신규 필드 삽입, `of`/`with*` 팩토리 전부 갱신).
- **고객 대면 팀 번호 노출**: `TeamResponse.teamNo`(팀 목록/신설), `PurchaseResponse.teamNo`(구매자
  구매내역), `BuyerTeamResponse.teamNo`(구매자 공구 참여 목록 — 계획 문서 태스크 목록엔 명시적으로
  안 나와 있었지만 "이미 teamId가 노출되는 자리엔 teamNo도 추가한다"는 설계 원칙을 일관 적용해
  포함시켰다), `SellerOrderResponse.teamNo`/`SellerTeamResponse.teamNo`(판매자 주문/공구 현황).
  `TeamJoinResponse`(join/leave 응답)·`RefundRequestResponse`는 계획에 명시되지 않아 그대로 뒀다.
- **admin 노출 명시적 제외**: `order_no`/`team_no`는 `AdminMemberResponse`/admin 상품 응답/
  `GET /api/admin/refund-requests` 어디에도 추가하지 않았다(확정 4·확정 1의 이연 결정 그대로).
- **프론트**: `admin-members.js`(회원번호 표시), `admin-products.js`(상품코드 표시),
  `admin/members.html`·`admin/products.html`(검색 placeholder에 코드 언급 추가), `product.js`
  (상품 상세: 상품코드 배지 신규 엘리먼트 `#product-code` + 팀 카드에 `teamNo`), `buyer-mypage.js`
  (구매내역·공구 참여 목록에 `teamNo`), `seller-mypage.js`(주문·공구 현황에 `teamNo`).
- **문서**: `docs/policy/identifier-code.md` 신설(포맷·채번·마이그레이션 순서·노출 범위 SSOT),
  `docs/db/member.md`/`product.md`/`payment.md`/`group_buy_team.md` 컬럼 명세 갱신,
  `docs/api/admin.md`/`product.md`/`team.md`/`mypage.md` 응답 예시 갱신,
  `docs/deploy-guide.md`에 "12. 회원번호·상품코드·주문번호·공구팀 번호 배포 절차" 신설(1단계
  nullable+백필 / 2단계 NOT NULL+UNIQUE 제약, `member.email` UNIQUE 사례와 같은 이유로 배포 시
  수동 확인이 필요함을 명시).

### 확정 판단 — 4개 컬럼을 이번 라운드엔 nullable로만 둔 이유

계획 문서(`docs/dev/ongoing/admin-identifier-codes.md` "리스크")가 이미 "컬럼을 nullable로 추가 →
애플리케이션 레벨 백필 → 그 다음에야 NOT NULL + UNIQUE 제약을 건다"는 순서를 요구했고, 태스크
목록도 "엔티티에 코드 컬럼 추가"와 "백필 완료 후 UNIQUE 제약 적용"을 **별개 태스크**로 분리해뒀다.

`ddl-auto: update`는 완전히 새 컬럼이라도 **NOT NULL이면서 기존 데이터가 있는 테이블**에는
DEFAULT 없이 안전하게 적용하기 어렵다(MySQL이 기존 행을 채울 값을 알 수 없음) — `docs/db/member.md`의
`email` UNIQUE 인덱스 사례("기존 컬럼에 나중에 건 제약은 `ddl-auto`가 자동으로 안 해준다")와 근본
원인이 같지만, 이번엔 컬럼 자체가 새로 생기고 값도 행마다 달라서(`member.email_verified`처럼
상수 DEFAULT를 못 씀) 문제가 한 단계 더 크다.

이 샌드박스 환경에는 실행 중인 MySQL/Redis가 전혀 없어(`docker ps`, `Test-NetConnection`으로 직접
확인) 실제 populated DB에 대고 검증할 방법이 없었다. CI(`ci.yml`)는 매번 완전히 빈 MySQL 컨테이너로
띄우므로 컬럼을 처음부터 `nullable=false, unique=true`로 둬도 CI만 놓고 보면 통과하겠지만, 그렇게
하면 이미 데이터가 있는 로컬 dev DB·Railway 프로덕션 DB에 이 코드를 배포하는 순간 컬럼 추가 자체가
실패할 위험이 있다고 판단해 **의도적으로 1단계(nullable + 백필 메커니즘)까지만 구현**하고,
2단계(제약 적용)는 배포 절차 문서(`docs/deploy-guide.md` 12절)로 남겨 별도 후속 배포로 미뤘다.

→ **이 판단 때문에 계획의 평가 기준 중 "백필 완료 후 4개 컬럼 모두 UNIQUE 제약이 정상 적용된다"는
이번 Generate 산출물의 엔티티 코드만으로는 충족되지 않는다.** Evaluate 단계에서 이 트레이드오프가
수용 가능한지(1단계까지만 인정하고 통과 처리할지, 재계획이 필요한지) 판단이 필요하다.

### 스코프 판단 메모

- `BuyerTeamResponse.teamNo`는 계획 태스크 목록에 문구로 명시되지 않았으나(구매자 구매내역/판매자
  주문·공구 현황만 언급), "이미 teamId가 원문 노출되는 자리엔 teamNo도 추가한다"는 계획의 일반
  설계 원칙이 이 API에도 그대로 적용되는 사례라 판단해 포함시켰다 — 범위 확장이 아니라 같은 규칙의
  누락 없는 적용으로 봤다.
- `docs/policy/identifier-code.md`는 회원/상품/주문/공구팀 4개 도메인이 공유하는 cross-cutting
  규칙이라 계획 그대로 별도 정책 문서로 분리했다(`docs/policy/README.md`의 SSOT 원칙).
- 공구팀 번호 포맷(`T0000001`)은 계획 문서가 "사용자가 직접 확인받은 값은 아니고 합리적 기본안"이라고
  명시한 값을 그대로 채택했다 — Generate 착수 전 재확인을 권장했었지만, 회원/상품과 동일한 단순형
  포맷이라 일관성 있고 위험이 낮다고 판단해 별도 확인 없이 진행했다. 이견이 있으면 재계획 대상.

### 검증

- `./gradlew compileJava` — BUILD SUCCESSFUL.
- `./gradlew compileTestJava` — 최초 실패(`CacheConfigTest`가 `ProductSummaryResponse`/
  `ProductResponse`를 포지셔널 생성자로 직접 만들고 있어 필드 삽입으로 인자 개수가 어긋남) →
  두 테스트의 생성자 호출에 `productCode` 인자(`"P0000001"`)를 삽입해 수정 → 재실행 BUILD SUCCESSFUL.
  이 저장소에 다른 위치에서 이 DTO들을 포지셔널로 생성하는 곳은 없음을 `compileTestJava` 전체
  통과로 확인.
- **`./gradlew test`(전체 테스트 실행)는 수행하지 않았다** — 이 환경에 MySQL/Redis가 전혀 떠 있지
  않아(`docker ps`, `Test-NetConnection localhost:3306/6379` 둘 다 실패) 애초에 돌릴 수 없었다.
  Evaluate 단계에서 MySQL/Redis가 있는 환경에서 실행 필요.
- API 응답 실측 샘플은 만들지 못했다(서버를 못 띄움) — 코드 레벨 정합성(DTO 필드·팩토리 메서드
  일관성, 리포지토리 쿼리 컴파일)만 확인했다.

### 다음 (Evaluate가 판단할 것)

- MySQL/Redis 있는 환경에서 `./gradlew test` 실행, 실제 API 응답에 각 필드가 기대대로 실리는지 확인.
- 위 "확정 판단"의 1단계-only 구현이 이번 라운드 평가 기준을 충족하는 것으로 볼지, 2단계(제약
  적용)까지 요구할지 판단 — 후자라면 populated DB 없는 안전한 검증 방법(예: 별도 테스트 프로파일에서
  빈 스키마로 nullable=false 상태를 직접 검증)을 Plan 단계에서 다시 논의해야 할 수 있다.

## 결과 (Evaluate) — 2026-08-22

**계산적 평가**: 사용자가 Docker Desktop을 띄운 뒤 `./gradlew test`(전체 스위트, MySQL·Redis 실제
가동) 실행 → `BUILD SUCCESSFUL`, JUnit XML 집계 `tests=509 skipped=0 failures=0 errors=0`. 회귀는
없다.

**추론적 평가 — 계획·정책 대조**: 태스크 체크리스트 대부분(엔티티 컬럼, 신규 생성 채번, admin
DTO/검색, 공개 API `productCode`, 팀/마이페이지 `teamNo`, `order_no`/`team_no` admin 미노출, 정책·
API·DB 문서 갱신)은 코드/문서 확인상 계획대로다. `orderNo`/`order_no`를 `src/main/java/.../dto/`
전체에 grep했을 때 단 한 곳도 없음을 확인했고(확정 4·확정 1 준수), `admin/members.html`·
`admin/products.html`에 코드 표시, `MemberRepositoryImpl`/`ProductRepositoryImpl`에 코드 검색
조건이 실제로 들어가 있음도 코드로 확인했다.

**다만 실제 실행 검증(API 왕복)에서 백필 메커니즘의 심각한 버그를 발견해 통과시킬 수 없다.**

### 원인 — `IdentifierCodeBackfillService`의 Spring 자가호출(self-invocation) 트랜잭션 무효화

`backfillMembers()`(`@Transactional` 없음)가 `ids.forEach(this::backfillMemberOne)`로
**같은 빈 안에서 `@Transactional` 메서드를 `this::`로 직접 호출**한다. Spring AOP는 프록시 기반이라
이렇게 자기 자신을 통해 호출하면 프록시를 거치지 않고 원본 메서드가 그대로 실행돼 `@Transactional`이
**조용히 무시된다**(잘 알려진 Spring 프록시 자가호출 함정). 그 결과 `backfillMemberOne` 안의
`memberRepository.findById(id)`가 자기만의(암묵적) 트랜잭션에서 끝나 엔티티가 detach되고, 그 뒤
`member.assignMemberCode(...)`로 필드를 바꿔도 flush될 영속성 컨텍스트가 없어 **DB에 전혀 반영되지
않는다.** `backfillProductOne`/`backfillPaymentOne`/`backfillTeamOne` 4개 메서드 전부 동일한 구조라
같은 버그를 갖고 있다.

참고로 이 구현이 참조한 선례(`SellerRevenueSummaryBackfillService.backfillOneIfMissing`)는 겉보기엔
같은 자가호출 패턴이지만 우연히 버그가 드러나지 않는다 — 그쪽은 조회한 엔티티를 변형(dirty
checking)하는 게 아니라 **새 엔티티를 만들어 `repository.save()`로 명시적으로 저장**하기 때문에,
`@Transactional`이 무시돼도 `save()` 자체가 Spring Data의 자체 트랜잭션 경계로 커밋된다. 반면
`IdentifierCodeBackfillService`는 `findById` + 필드 변경 + (암묵적 flush 기대) 패턴이라 자가호출
문제의 영향을 그대로 받는다 — 로그만 "성공"으로 찍히고 실제로는 아무 것도 안 바뀌는 조용한 실패라
발견하기 어렵다.

### 증거 (API/DB 샘플)

1. **신규 생성 채번은 정상 동작** — 처음엔 실행 중이던 `app` 컨테이너 이미지가 2026-08-21 10:32
   빌드(이번 identifier-code 변경 이전)라 재현이 왜곡됐다. `docker compose build app && docker
   compose up -d app`로 최신 코드 반영 후 재검증:
   - `POST /api/auth/signup` (username=eval_test_user2) → `201 {"memberId":1514,...}`.
   - DB: `SELECT id, member_code FROM member WHERE id=1514` → `M0001514` (포맷 일치, `M` + PK 7자리).
   - `admin`으로 승격(로컬 DB 직접 UPDATE, 테스트 목적) 후 `GET /api/admin/members?page=0&size=3` →
     `{"memberId":1514,"memberCode":"M0001514",...}` — admin 응답에 정상 노출.
   - `GET /api/admin/members?search=M0001514` → 해당 회원 1건 조회됨 (코드 검색 정상).
   - `ProductService.register()`/`PaymentService.create()`/`TeamService.create()` 코드를 확인한 결과
     신규 생성 경로는 전부 "저장 직후 같은 트랜잭션 메서드 안에서 직접 `assignXxx` 호출" 패턴이라(
     자가호출 아님) `MemberService.signup()`과 동일하게 정상 동작할 구조로 판단된다(직접 회원가입
     경로로 실측 확인, 상품/결제/팀은 같은 패턴이라 구조적으로 검증).
2. **백필은 실패** — `member_code`가 없는 기존 행(`id=1513`, 재빌드 전 이미지로 만들어진 회원)을 남긴
   채, 별도 컨테이너를 `APP_BACKFILL_IDENTIFIER_CODE=true`로 1회 기동:
   ```
   INFO IdentifierCodeBackfillRunner  : 식별 코드 백필 실행기 시작
   INFO IdentifierCodeBackfillService : member_code 백필 완료: 대상행수=1
   INFO IdentifierCodeBackfillService : 식별 코드 백필 전체 완료: memberCode=1, ...
   INFO IdentifierCodeBackfillRunner  : 식별 코드 백필 실행기 종료: 총갱신행수=1
   ```
   로그는 "1건 갱신"을 주장하지만, 백필 실행 후 `SELECT id, member_code FROM member WHERE id=1513`은
   여전히 `NULL`이다. 로그와 실제 DB 상태가 어긋난다 — 즉 백필이 **거짓 성공 로그**를 남긴다.
3. 테스트 데이터(`member.id=1513,1514`)는 평가 종료 후 DB에서 삭제해 정리했다. `app` 컨테이너는
   정상 이미지로 재기동해 원상 복구했다.

### 판정

**Evaluate 통과 아님(FAIL).** 계획의 태스크("기존 데이터 백필 배치 — 4개 테이블 전부")와 평가 기준
("기존 회원/상품/결제/공구팀 전체 행의 코드 백필이 끝나 NULL이 하나도 없고…")이 요구하는 백필 기능이
**동작하지 않는다** — 이건 nullable/제약 이연 판단(계획의 "리스크" 섹션이 이미 필요하다고 명시한
순서, 별도 논쟁 대상 아님)과는 별개의, 명백한 **구현 결함**이다. nullable+UNIQUE 이연 자체는 계획의
리스크 섹션 문구("컬럼을 nullable로 추가 → 애플리케이션 레벨 백필 → 그 다음에야 NOT NULL+UNIQUE
제약을 건다") 및 `member.email` UNIQUE 인덱스 선례와 같은 이유로 프로덕션 안전을 위해 정당하다고
판단되지만, 그 전제인 **백필 자체가 실제로 동작해야** 의미가 있다 — 지금 상태로 배포하면
`docs/deploy-guide.md` 12-1절의 "백필 로그 확인" 절차조차 거짓 성공을 보고 다음 단계로 진행하게
만들어 위험하다.

### 다음 (Generate 재시도 — 같은 접근으로 고칠 수 있는 실패, 재계획 불필요)

- `IdentifierCodeBackfillService`의 `backfillMemberOne`/`backfillProductOne`/`backfillPaymentOne`/
  `backfillTeamOne` 4개 메서드가 자가호출로 `@Transactional`이 무시되는 문제를 고친다. 예:
  `repository.save(entity)`를 변경 후 명시적으로 호출하거나(선례와 같은 방식), 자가호출을 피하도록
  구조를 바꾼다(예: 별도 컴포넌트로 분리해 진짜 프록시를 통해 호출, 또는 `@Modifying` 벌크 UPDATE
  쿼리로 전환).
- 고친 뒤 이번에 했던 것과 같은 방식(별도 컨테이너 + `APP_BACKFILL_IDENTIFIER_CODE=true` 기동,
  백필 전/후 `SELECT ... WHERE xxx_code IS NULL` 대조)으로 실제 DB 반영을 재확인한다 — 로그 메시지만
  보고 통과로 인정하지 않는다.
- nullable+제약 이연(1단계-only) 판단 자체는 재검토 불필요 — 계획의 리스크 섹션과 `member.email`
  선례에 부합하는 정당한 설계로 판단됨. 백필이 실제로 동작하는 것을 확인한 뒤, 최종 판단(2단계
  NOT NULL+UNIQUE 적용을 이번 라운드에 포함할지, `docs/deploy-guide.md` 12-2절처럼 별도 배포
  단계로 남길지)은 다음 Evaluate에서 다시 판단한다.

## Attempt 2 — 2026-08-22

### 시도

Evaluate가 실측으로 확인한 자가호출(self-invocation) 트랜잭션 버그만 고쳤다. 계획(백필 서비스
구조 자체)은 바꾸지 않았다 — `IdentifierCodeBackfillService`의 `backfillMembers()`/
`backfillProducts()`/`backfillPayments()`/`backfillTeams()`가 여전히 `ids.forEach(this::backfillXxxOne)`
로 같은 빈 안의 `@Transactional` 메서드를 호출하는 구조는 그대로 두고, 각 `backfillXxxOne` 메서드
안에서 `assignXxxCode(...)`로 필드를 바꾼 직후 **명시적으로 `repository.save(entity)`를 호출**하도록
4곳(`backfillMemberOne`/`backfillProductOne`/`backfillPaymentOne`/`backfillTeamOne`) 모두 한 줄씩
추가했다.

이 방향을 고른 이유: 이 구현이 원래 참조한 선례 `SellerRevenueSummaryBackfillService.backfillOneIfMissing`
이 자가호출 상태에서도 정상 동작하는 이유가 정확히 "새 엔티티를 만들어 `save()`로 명시적으로 저장하기
때문"(Evaluate 원인 분석에 이미 적혀 있음)이라, 같은 해법을 그대로 적용하면 기존 구조(행 1개당
트랜잭션 분리, `@Transactional` 유지, 조회 경로 미호출 등)를 하나도 안 바꾸고 가장 적은 변경으로
버그만 제거할 수 있다고 판단했다. self-invocation 자체를 없애는 구조 변경(별도 컴포넌트 분리, 벌크
UPDATE 전환)은 계획에 없던 범위 확장이라 선택하지 않았다.

### 검증

- `./gradlew compileJava` — BUILD SUCCESSFUL.
- 로컬 docker-compose 환경(`docker ps`로 mysql/redis/app 모두 기동 확인)에서 실제 DB 왕복으로
  검증했다. 로그 메시지만 보고 통과로 인정하지 않기 위해 매 단계 DB를 직접 조회했다.
  1. `docker exec ... mysql` 로 4개 테이블 전체가 비어 있음(`total=0`) 확인.
  2. 4개 테이블에 각각 `member_code`/`product_code`/`team_no`/`order_no`가 **NULL인 테스트 행을
     직접 INSERT**로 만들었다(`member.id=1515`, `product.id=702`, `group_buy_team.id=159`,
     `payment.id=425`, 서로 FK로 연결). INSERT 직후 SELECT로 4개 컬럼 전부 NULL임을 재확인.
  3. `docker compose build app`으로 이번 수정을 반영한 이미지를 재빌드.
  4. 그 이미지로 `docker run --rm --network gong9ri-main_default -e APP_BACKFILL_IDENTIFIER_CODE=true ...`
     one-shot 컨테이너를 별도로 기동(기존에 떠 있던 `gong9ri-main-app-1`은 건드리지 않음). 로그:
     ```
     INFO IdentifierCodeBackfillRunner  : 식별 코드 백필 실행기 시작
     INFO IdentifierCodeBackfillService : member_code 백필 완료: 대상행수=1
     INFO IdentifierCodeBackfillService : product_code 백필 완료: 대상행수=1
     INFO IdentifierCodeBackfillService : order_no 백필 완료: 대상행수=1
     INFO IdentifierCodeBackfillService : team_no 백필 완료: 대상행수=1
     INFO IdentifierCodeBackfillService : 식별 코드 백필 전체 완료: memberCode=1, productCode=1, orderNo=1, teamNo=1
     INFO IdentifierCodeBackfillRunner  : 식별 코드 백필 실행기 종료: 총갱신행수=4
     ```
  5. **로그를 신뢰하지 않고** 백필 후 DB를 직접 SELECT한 결과:
     ```
     t        id    code
     member   1515  M0001515
     product  702   P0000702
     team     159   T0000159
     payment  425   O20260822-000425
     ```
     4개 전부 형식(`M`+PK7자리, `P`+PK7자리, `T`+PK7자리, `O`+결제일8자리+`-`+PK6자리)에 맞게 실제로
     채워졌다 — 1차 시도 때와 달리 로그와 DB 상태가 일치한다.
  6. one-shot 컨테이너를 정지(`docker stop`)하고, 테스트로 넣은 4개 행(payment→team→product→member
     순, FK 역순)을 전부 DELETE해 원상 복구. 삭제 후 4개 테이블에서 해당 id가 0건임을 재확인.
     기존에 떠 있던 `gong9ri-main-app-1` 컨테이너는 `APP_BACKFILL_IDENTIFIER_CODE` 환경변수가
     없음을 확인해 백필이 의도치 않게 재실행되지 않음을 확인, 별도 조치 없이 그대로 뒀다.

### 범위

- 신규 채번/admin 노출/공개 API 노출 등 1차 시도에서 이미 정상 확인된 부분은 건드리지 않았다.
- 커밋하지 않았다.

## 결과 (2차 Evaluate) — 2026-08-22  ✅ PASS

Attempt 2에서 본인이 스스로 검증한 결과를 그대로 믿지 않고 독립적으로 재검증했다.

### 계산적 평가

- `./gradlew test`(전체 스위트, 로컬 docker-compose MySQL·Redis 실제 가동) 1차 실행 →
  `BUILD FAILED`, `ProductCachingTest` 4개 실패(`expected: <15> but was: <16>` 등). **원인 조사 결과
  이 기능의 회귀가 아니라 Evaluate 본인이 재검증용으로 DB에 직접 넣어둔 leftover 테스트 상품 행
  (`product.id=909`, `hidden=0`)이 남아 있어 `ProductCachingTest`의 상품 개수 카운트를 오염시킨
  것으로 확인됐다** — `ProductCachingTest`만 단독 재실행해도 같은 실패가 재현됐고,
  `SELECT COUNT(*) FROM product WHERE hidden=0`이 leftover 행 1건만 남아 있음을 확인해 원인을
  특정했다. 테스트 데이터를 정리(FK 역순 DELETE)한 뒤 전체 스위트를 재실행 → `BUILD SUCCESSFUL`,
  JUnit XML 집계 `tests=509 skipped=0 failures=0 errors=0`(`ProductCachingTest` 단독 재확인도
  `tests="6" failures="0"`). 회귀 없음.

### 추론적 평가 — 코드 재검토 (백필 수정의 타당성)

`IdentifierCodeBackfillService.java`를 직접 읽어 4개 메서드
(`backfillMemberOne`/`backfillProductOne`/`backfillPaymentOne`/`backfillTeamOne`) 전부에
`assignXxxCode(...)` 직후 `repository.save(entity)`가 실제로 추가돼 있음을 확인했다.

이 수정이 자가호출(self-invocation) 문제를 실제로 해결하는지 판단: `backfillXxxOne`이 자가호출로
`@Transactional`이 무시되더라도, `repository.save(...)` 호출 자체는 **그 리포지토리 빈의 프록시를
통한 별개 호출**이라 self-invocation의 영향을 받지 않는다. `SimpleJpaRepository.save()`는 그 자체가
`@Transactional`이고, PK가 이미 존재하는(=detached) 엔티티에 대해서는 `entityManager.merge(...)`를
호출해 트랜잭션 커밋 시 실제 UPDATE를 발생시킨다 — 즉 이 한 줄 추가만으로 조회~변경~flush 전체가
`save()`의 트랜잭션 경계 안에서 완결된다. 1차 Evaluate가 지목한 선례(`SellerRevenueSummaryBackfillService`)와
동일한 원리이므로 타당한 수정으로 판단했다.

### 독립 재검증 — 직접 실측 (수정한 사람의 로그를 신뢰하지 않고 처음부터 재현)

1. `docker ps`로 mysql/redis/app 3개 컨테이너 가동 확인.
2. 재검증 시작 시점에 4개 테이블(`member`/`product`/`payment`/`group_buy_team`) 전부 코드 컬럼
   NULL 0건임을 확인(이전 Attempt 2가 테스트 데이터를 정상적으로 원상복구했음을 재확인).
3. 4개 테이블에 각각 `member_code`/`product_code`/`team_no`/`order_no`가 **NULL인 새 테스트 행을
   직접 INSERT**(`member.id=1603`, `product.id=909`, `group_buy_team.id=210`, `payment.id=530`,
   FK로 연결). INSERT 직후 SELECT로 4개 컬럼 전부 NULL 재확인.
4. `docker compose build app`으로 현재 작업트리 소스 기준 이미지를 재빌드(이번 세션에서 새로
   빌드해, Attempt 2가 남긴 이미지를 그대로 믿지 않았다).
5. 그 이미지로 `docker run --rm --network gong9ri-main_default -e APP_BACKFILL_IDENTIFIER_CODE=true ...`
   one-shot 컨테이너를 별도 기동(기존 `gong9ri-main-app-1`은 건드리지 않음). 로그:
   ```
   INFO IdentifierCodeBackfillRunner  : 식별 코드 백필 실행기 시작
   INFO IdentifierCodeBackfillService : member_code 백필 완료: 대상행수=1
   INFO IdentifierCodeBackfillService : product_code 백필 완료: 대상행수=1
   INFO IdentifierCodeBackfillService : order_no 백필 완료: 대상행수=1
   INFO IdentifierCodeBackfillService : team_no 백필 완료: 대상행수=1
   INFO IdentifierCodeBackfillService : 식별 코드 백필 전체 완료: memberCode=1, productCode=1, orderNo=1, teamNo=1
   INFO IdentifierCodeBackfillRunner  : 식별 코드 백필 실행기 종료: 총갱신행수=4
   ```
6. **로그를 신뢰하지 않고** 컨테이너를 정지시킨 뒤 DB를 직접 SELECT:
   ```
   t        id    code
   member   1603  M0001603
   product  909   P0000909
   team     210   T0000210
   payment  530   O20260822-000530
   ```
   4개 전부 포맷대로 실제로 채워졌다 — 로그와 DB 상태가 일치, Attempt 2의 자체 보고와 독립적으로
   재현됨.
7. 테스트 행 4건을 FK 역순(payment→team→product→member)으로 DELETE해 원상복구, 삭제 후 0건 재확인.
   one-shot 컨테이너는 `--rm`으로 자동 정리됨. 기존 `gong9ri-main-app-1`은 건드리지 않았다.
8. 정리 직후 실수로 leftover 상품 행이 `./gradlew test`(1차 실행)에 섞여 `ProductCachingTest`를
   오염시켰던 사실이 위 "계산적 평가"에 기록돼 있다 — 이는 백필 기능 자체의 결함이 아니라 이번
   Evaluate가 재검증 도중 만든 테스트 데이터의 정리 타이밍 문제였다.

### 계획 대조 — 태스크·평가기준 항목별 확인

- 정책 문서(`docs/policy/identifier-code.md`) 존재 확인, 범위·포맷·채번 방식·마이그레이션 순서·
  노출 범위 표가 계획의 "확정 1~5"와 문구까지 일치함을 직접 읽어 확인.
- `docs/db/member.md`/`product.md`/`payment.md`/`group_buy_team.md` 4개 파일 모두 새 컬럼 행이
  `git diff`로 실제 추가돼 있고, 타입/제약/설명이 정책 문서와 일치함을 확인.
- `docs/api/admin.md`/`product.md`/`team.md`/`mypage.md` 4개 파일 모두 `git diff`로 실제 응답
  예시·설명이 추가됐음을 확인(`memberCode`/`productCode`/`teamNo` 노출 위치가 계획대로).
- `order_no`를 `src/main/java/.../dto/` 전체에 grep → 0건(admin 미노출 유지), `teamNo`를
  `AdminMemberResponse.java`에 grep → 0건(공구팀 번호 admin 미노출 유지) — 둘 다 재확인.
- `IdentifierCodeFormatter`의 4개 메서드 포맷이 정책 문서 표와 정확히 일치, 위 5번 실측 코드값들과도
  일치함을 확인.
- 신규 채번 호출부(`MemberService`/`ProductService`/`PaymentService`/`TeamService`)는 저장 직후
  같은 트랜잭션에서 `assignXxx(...)`를 직접 호출하는 구조(자가호출 아님)임을 grep으로 재확인 —
  Attempt 1때 실측된 정상 동작 구조 그대로 유지됨.
- Admin/고객 대면 프론트(`admin-members.js`/`admin-products.js`)에 `memberCode`/`productCode`
  표시 코드가 실제로 있음을 확인.
- `MemberRepositoryImpl`/`ProductRepositoryImpl`의 코드 검색 조건(`containsIgnoreCase`)이 백필
  전 NULL 행에 대해서도 안전하게 평가됨(QueryDSL null 컬럼 비교는 매치 안 함으로 처리, NPE 없음)을
  코드로 확인.
- **UNIQUE 제약(2단계)은 이번 라운드에 여전히 미적용** — 이는 계획의 "리스크" 섹션이 이미 요구한
  1단계-only 스코프이자, 1차 Evaluate 판정문이 "재검토 불필요"로 명시한 사항이라 이번에도 그대로
  인정한다. 백필이 실제로 동작함이 확인된 지금 시점부터 2단계(NOT NULL+UNIQUE)를 별도 배포로 안전하게
  진행할 수 있다.

### 판정

**Evaluate 통과(PASS).** 백필 버그가 실제로 수정됐음을 독립 재현으로 확인했고, 계획의 태스크·평가
기준을 항목별로 재대조해 전부 충족함을 확인했다. `./gradlew test` 509개 전부 통과(회귀 없음).
`docs/dev/identifier-code/design.md`를 신설했고, `docs/dev/ongoing/admin-identifier-codes.md`를
`docs/dev/identifier-code/changes/001-admin-identifier-codes.md`로 채번 이동했다.

### 남는 후속 작업 (이번 라운드 스코프 밖, 계획 문서에도 이미 "이연"으로 명시됨)

- 2단계: 백필 완료를 배포 환경에서도 확인한 뒤 4개 컬럼에 `NOT NULL + UNIQUE` 제약 적용
  (`docs/deploy-guide.md` 12-2절).
- `order_no`의 admin 노출(환불요청 응답 확장 또는 admin 전용 주문 목록 화면).
- `team_no`의 admin 노출(admin 전용 공구팀 목록 화면).
