# identifier-code — 사람이 읽는 식별 코드 (회원번호 · 상품코드 · 주문번호 · 공구팀 번호)

## 규칙

### 범위 — 4종

회원번호(`member.member_code`) · 상품코드(`product.product_code`) · 주문번호(`payment.order_no`) ·
공구팀 번호(`group_buy_team.team_no`)에 적용한다. 기존 PK(`Long id`)는 그대로 내부 식별자·FK로
유지하며, 이 코드들은 **PK 옆에 추가되는 별도 컬럼**이다 — 연관관계(FK)를 바꾸지 않는다.

### 포맷 — 접두어 + PK 파생

| 도메인 | 컬럼 | 포맷 | 예시 |
|---|---|---|---|
| 회원 | `member.member_code` | `M` + PK 7자리 zero-pad | `M0000001` |
| 상품 | `product.product_code` | `P` + PK 7자리 zero-pad | `P0000001` |
| 주문(결제) | `payment.order_no` | `O` + 결제 접수일(`paidAt`, `yyyyMMdd`) + `-` + PK 6자리 zero-pad | `O20260822-000001` |
| 공구팀 | `group_buy_team.team_no` | `T` + PK 7자리 zero-pad | `T0000001` |

- 회원/상품/공구팀은 날짜 접두어를 넣지 않는다 — CS 응대에서 필요한 건 "몇 번째인지" 특정이지
  가입/등록/팀 신설 시점별 그룹핑이 아니라서, 접두어가 자릿수만 늘린다고 판단했다.
- 주문번호만 날짜를 넣는다 — 정산 대사·일자별 CS 조회 편의상 날짜를 포함하는 실무 관행을 따랐다.
- 포맷 구현은 `com.gong9ri.gong9ri.common.identifier.IdentifierCodeFormatter`(단일 SSOT).

### 채번 방식 — PK 파생 (별도 카운터 테이블 없음)

- auto-increment PK가 이미 유일·순차적이므로 그 값을 코드로 변환만 한다. 별도 시퀀스/카운터
  테이블이 없어 카운터 증가 자체의 동시성 제어가 필요 없다(PK 채번은 DB가 이미 원자적으로 보장).
- 채번 시점: 각 엔티티가 저장되어 PK가 확정된 직후(같은 트랜잭션 안에서) 1회 — 회원가입/카카오
  신규가입(`MemberService`), 상품 등록(`ProductService.register`), 결제 요청 접수
  (`PaymentService.create`), 공구팀 신설(`TeamService.create`).
- 코드는 채번된 뒤 **불변**이다(PK 파생이라 PK가 안 바뀌면 코드도 안 바뀐다) — 공구팀의 경우
  `status`가 `RECRUITING → SUCCESS`/`FAILED`로 바뀌어도 `team_no`는 그대로다.
- 트레이드오프: 코드가 사실상 PK를 노출하는 셈이라, 코드값만으로 전체 회원수/상품수/주문수/공구팀
  수 규모를 추정할 수 있다(상품코드는 공개 API에도 실리므로 이 추정 가능성도 함께 공개된다). 별도
  카운터 테이블 방식(연도별 리셋 등)은 카운터 증가 시 동시 요청 정합성 보장이 새로 필요해지므로
  채택하지 않는다.

### 마이그레이션 — nullable 추가 → 애플리케이션 백필 → NOT NULL/UNIQUE 제약

기존 데이터가 있는 테이블에 "행마다 값이 다른" NOT NULL 컬럼을 한 번에 추가할 수 없다(상수
`@ColumnDefault`로 채울 수 있는 `emailVerified`/`hidden` 같은 컬럼과 다르다). 그래서 아래 순서를
반드시 지킨다:

1. 컬럼을 **nullable**로 추가한다(현재 이 4개 컬럼의 상태). 신규 생성 경로는 저장 직후 바로
   채번하므로 새 행은 항상 값이 있다.
2. 기존 행(이 컬럼이 생기기 이전에 만들어진 행)은 `IdentifierCodeBackfillService` +
   `IdentifierCodeBackfillRunner`(opt-in, `app.backfill.identifier-code=true`)로 애플리케이션
   레벨 백필한다.
3. 4개 컬럼 모두 NULL이 0건임을 확인한 뒤에야 엔티티를 `nullable=false, unique=true`로 바꾸고
   재배포한다 — 그 전에 바꾸면 기존 데이터가 있는 DB에서 `ddl-auto: update`의 컬럼 제약 변경
   자체가 실패할 수 있다.

배포 환경별 구체 절차는 `docs/deploy-guide.md`의 "회원번호·상품코드·주문번호·공구팀 번호 배포
절차" 참고.

### 노출 범위

| 도메인 | admin | 공개/고객 대면 |
|---|---|---|
| 회원번호 | O (회원 목록 화면 + 검색) | 노출 안 함(개인정보 성격) |
| 상품코드 | O (상품 현황 화면 + 검색, 공개 목록과 공유 DTO) | O (공개 상품 목록/상세 API) |
| 주문번호 | **노출 안 함(이연)** — admin 전용 주문 목록 화면이 아직 없음 | 노출 안 함(스코프 밖) |
| 공구팀 번호 | **노출 안 함(이연)** — admin 전용 공구팀 목록 화면이 아직 없음 | O (상품 상세 팀 카드, 마이페이지 구매내역/공구 참여/주문 현황) |

admin 전용 주문 목록 화면·공구팀 목록 화면이 생기면 그때 `order_no`/`team_no`를 그 화면에 노출하는
별도 작업을 진행한다.

## 근거 / 배경

관리자(Admin)가 CS·운영 업무를 볼 때 순수 auto-increment PK(`Long id`)만으로 회원/상품/결제/공구팀을
식별하는 건 실무에서 불편하다(동명이인·오탈자 구분 어려움, 재고·발주·바코드 연동 어려움, 정산·배송
문의 조회 어려움). 상세 배경·조사 경위는 `docs/dev/ongoing/admin-identifier-codes.md` 참고.

## 적용 대상

- `member/signup`, `auth/social-login`(회원번호 채번)
- `product/register`(상품코드 채번), `product/list`, `product/detail`(공개 API 노출)
- `payment/crud`(주문번호 채번)
- `team/crud`(공구팀 번호 채번), `product/detail`(팀 카드 노출), `mypage/view`(구매내역·공구 참여·
  주문 현황 노출)
- `admin/members`, `admin/products`(admin 노출 + 검색)
