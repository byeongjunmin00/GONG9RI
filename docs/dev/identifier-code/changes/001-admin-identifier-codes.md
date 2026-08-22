# 회원번호 · 상품코드 · 주문번호 · 공구팀 번호 도입

대상: identifier-code            <!-- 완료 시 docs/dev/identifier-code/design.md 신설 + changes/001-*.md로 채번 이동 -->
<!-- 최초엔 "admin"으로 잡았으나, 확정 범위가 admin뿐 아니라 공개 상품 API·공구팀(고객 대면 화면)까지
     걸치는 cross-cutting 식별 코드 체계라 admin 하위가 아니라 독립 개념으로 승격한다
     (docs/dev-doc-guide.md의 "환불처럼 여러 개념에 걸치는 것은 자기 개념으로 독립" 원칙과 동일). -->
담당: 전용운

## 배경 / 요구

관리자(Admin)가 CS·운영 업무를 볼 때 지금은 순수 auto-increment PK(`Long id`)로만 회원/상품/결제/
공구팀을 식별한다. 실무에서 흔히 쓰는 "회원번호"(CS 응대 시 동명이인·오탈자 없이 특정 회원 특정),
"상품코드/SKU"(재고·발주·바코드), "주문번호"(정산·배송 문의·CS 조회) 같은 사람이 읽기 좋은 식별
코드가 없다는 문제의식에서 시작. 사용자(전용운) 요청으로 GONG9RI에도 도입 계획을 세운다.

**원칙**: 기존 PK(`Long id`)는 그대로 내부 식별자·FK로 유지한다. 이번 작업은 그 옆에 사람이 읽는
코드를 **별도 컬럼으로 추가**하는 것이며, 연관관계(FK)를 바꾸는 마이그레이션은 하지 않는다.

## 사전 조사 결과 (현재 상태)

- `Member`/`Product`/`Payment`/`GroupBuyTeam` 전부 PK는 `@GeneratedValue(IDENTITY) Long id`뿐이고,
  사람이 읽는 식별 코드 필드가 없다 (`entity/Member.java`, `entity/Product.java`,
  `entity/Payment.java`, `entity/GroupBuyTeam.java`).
- `Product`에는 색상/사이즈 같은 진짜 옵션(variant) 구조가 없다. `docs/db/price_tier.md`의 가격구간은
  "공동구매 인원수 구간별 가격"이지 상품 옵션이 아니다 — 즉 SKU를 옵션 단위로 쪼갤 필요가 없고,
  **상품 1개 = 코드 1개**로 충분하다.
- 주문 역할은 별도 `Order` 엔티티가 아니라 `Payment`가 겸한다. `Payment.pgPaymentId`는 PortOne이
  채번하는 문자열이라 예측 불가능하고 PG 연동 전용이라 CS 응대용 주문번호로 못 쓴다.
- `docs/db/`의 `member.md`/`product.md`/`payment.md`/`group_buy_team.md` 전부 `id BIGINT PK auto`만
  있고, 회원번호/상품코드/주문번호/팀번호는 설계된 적이 없다 — 이번이 최초 도입.
- `docs/policy/`에는 이런 식별 코드 포맷/채번 규칙이 없다.
- Admin은 이미 회원 목록(`GET /api/admin/members`, `admin/members.html`)과 상품 현황
  (`GET /api/admin/products`, `admin/products.html`) 화면이 있어 코드 표시를 얹기 쉽다. 반면 **주문
  (결제)·공구팀 전용 admin 목록 화면은 아직 없다** — `GroupBuyTeam`은 admin 상품 카드에 진행률만
  인라인으로 나오고 별도 팀 목록 화면이 없으며, `Payment`가 admin 표면에 나오는 곳은
  `GET /api/admin/refund-requests`(환불 요청이 있는 결제만) 하나뿐이다. 이 사실이 아래 "확정 4"
  (주문번호)와 "확정 1"(공구팀 번호)의 admin 노출 범위를 좌우한다.
- **공구팀은 admin 전용 화면이 아니라 고객(구매자) 대면 화면에 이미 노출되고 있다**: 별도의 "팀 상세
  페이지"는 없고, 상품 상세 페이지(`product.html`/`product.js`)가 `GET /api/products/{productId}/teams`
  응답을 받아 그 상품에 딸린 모집 중인 팀들을 카드로 나열한다(참여자 목록·진행률·마감일 포함, 사실상
  "팀 상세"에 해당하는 정보량). 이 카드마다 `teamId`가 이미 원문 그대로 응답에 들어 있다
  (`docs/api/team.md`). 또한 마이페이지(구매자 구매내역·판매자 주문/공구 현황, `docs/api/mypage.md`)도
  같은 방식으로 `teamId`를 원문 그대로 노출 중이다 — 즉 팀 식별자를 사람이 읽는 코드로 바꿔 노출할
  자연스러운 자리는 admin이 아니라 **이 두 곳(상품 상세 팀 목록, 마이페이지)**이다.
- `AdminMemberResponse`가 `Long memberId`를 API에 그대로 노출 중이고, `admin/products.html`이 쓰는
  `ProductPageResponse`는 **공개 상품 목록(`GET /api/products`)과 같은 DTO**(`ProductSummaryResponse`)를
  공유한다 — 상품코드는 이 공유 DTO를 통해 공개 API에도 함께 노출된다(아래 "확정 3" 참고).

## 설계

### 확정 1. 범위 — 회원 + 상품 + 주문 + 공구팀 (4종)

회원번호(`member_code`) + 상품코드(`product_code`) + 주문번호(`order_no`, `Payment` 기준) +
**공구팀 번호(`team_no`, `GroupBuyTeam` 기준)** 까지 이번 스코프에 포함한다.

- **공구팀 번호의 노출 위치는 admin이 아니다.** 위 사전 조사대로 공구팀은 admin 전용 조회 화면이
  없는 대신 구매자가 직접 참여·조회하는 고객 대면 화면(상품 상세 페이지의 팀 목록, 마이페이지)에 이미
  `teamId`가 노출되고 있다. 그래서 `team_no`도 **그 두 화면(상품 상세 팀 목록, 마이페이지 구매내역/
  판매자 주문 현황)에 노출하는 것을 기본으로 한다** — 없는 admin 화면을 새로 만들기보다, 이미 고객이
  보고 있는 자리에 사람이 읽는 번호를 놓는 게 자연스럽다.
- **admin 쪽 공구팀 번호는 이번 라운드엔 컬럼·채번까지만 한다.** `order_no`(확정 4)와 동일한 판단이다
  — admin에 공구팀 전용 목록 화면이 아예 없어서, 없는 화면에 필드를 얹을 수 없다. admin 상품 현황
  카드의 진행률 인라인 표시에 `team_no`를 끼워 넣는 것도 고려했으나, 그 카드는 "상품별 대표 팀 진행률"
  스냅샷이라 특정 팀 하나를 CS 조회 단위로 짚어주는 용도가 아니라서 이번엔 보류한다. admin 전용 공구팀
  목록 화면이 생기면(별도 작업) 그때 `team_no`를 그 화면에 노출한다.

### 확정 2. 포맷 — 접두어 + PK 파생 코드

기존 PK는 그대로 두고, PK가 정해진 직후(insert 후) 그 값을 사람이 읽는 코드 문자열로 변환해 같은
행의 새 컬럼에 저장하는 방식(PK 파생)을 쓴다.

| 도메인 | 컬럼 | 포맷 | 예시 |
|---|---|---|---|
| 회원 | `member.member_code` | `M` + PK 7자리 zero-pad | `M0000001` |
| 상품 | `product.product_code` | `P` + PK 7자리 zero-pad | `P0000001` |
| 주문(결제) | `payment.order_no` | `O` + 결제 접수일(`paidAt`, `yyyyMMdd`) + `-` + PK 6자리 zero-pad | `O20260822-000001` |
| 공구팀 | `group_buy_team.team_no` | `T` + PK 7자리 zero-pad | `T0000001` |

- **회원/상품/공구팀은 날짜 접두어를 넣지 않는다** — CS 응대에서 필요한 건 "몇 번째 회원/상품/팀인지"
  특정이지 가입/등록/팀 신설 시점별 그룹핑이 아니라서, 접두어가 자릿수만 늘린다고 판단했다.
- **주문번호만 날짜를 넣는다(확정)** — 실무에서 주문번호는 정산 대사·일자별 CS 조회 편의상 날짜를
  포함하는 관행이 흔하고, 사용자가 이 포맷을 확정했다: `O` + 결제 접수일(`paidAt`, `yyyyMMdd`) +
  `-` + PK 6자리 zero-pad, 예시 `O20260822-000001`.
- **공구팀 번호 포맷은 회원/상품과 동일한 단순형(`T0000001`)을 기본 제안으로 문서화한 것**이지,
  주문번호처럼 명시적으로 확인받은 값은 아니다(사용자가 직접 답한 건 주문번호 포맷뿐, 공구팀 포맷은
  이번 계획 작성 중 합리적으로 유추한 기본안). Generate 착수 전에 이 포맷으로 진행해도 되는지 한 번 더
  가볍게 확인하는 것을 권장한다.

### 확정 3. 채번 방식 — PK 파생 (별도 카운터 테이블 없음)

- auto-increment PK가 이미 유일·순차적이므로 그 값을 코드로 변환만 한다. 별도 시퀀스/카운터 테이블이
  없어 구현이 단순하고, 카운터 증가 자체의 동시성 제어가 필요 없다(PK 채번은 DB가 이미 원자적으로
  보장). 기존 데이터 백필도 "행마다 자기 PK(+주문번호는 자기 `paidAt`)를 코드로 바꾸는" 단순 배치로
  끝난다.
- 트레이드오프로 남겨두는 사실: 코드가 사실상 PK를 그대로 노출하는 셈이라, 코드값만으로 전체
  회원수/상품수/주문수/공구팀 수 규모를 추정할 수 있다(상품코드는 공개 API에도 실리므로 이 추정
  가능성도 함께 공개된다 — 아래 "리스크" 참고). 별도 카운터 테이블 방식(연도별 리셋 등 더 유연)은
  카운터 증가 시 동시 요청 정합성 보장이 새로 필요해지므로 채택하지 않는다.

### 확정 4. 주문번호(`order_no`) admin 노출 — 이번 라운드는 컬럼·채번까지만

admin에는 아직 결제(주문) 전용 목록 화면이 없다. 기존 계획에서 검토했던 "환불요청 응답
(`GET /api/admin/refund-requests`)에 `orderNo` 추가"는 **이번 스코프에서 제외하고 다음 작업으로
이연한다.** 이번 라운드는 `payment.order_no` 컬럼 추가·신규 결제 채번·기존 결제 백필까지만 하고,
admin 어디에도 `orderNo`를 응답으로 내려주지 않는다. admin 전용 주문 목록 화면이 생기거나
`refund-requests` 응답 확장이 필요해지는 시점에 별도 계획으로 진행한다.

### 확정 5. 상품코드(`product_code`) 공개 API 노출 — 허용

`admin/products.html`이 쓰는 `ProductPageResponse`는 공개 상품 목록(`GET /api/products`)과 **같은
DTO**(`ProductSummaryResponse`)를 공유한다. `product_code`를 그대로 이 공유 DTO에 추가해 **공개
상품 목록·상세 API에도 노출한다.** 상품코드(SKU)는 회원번호와 달리 개인정보가 아니고, 실제 이커머스도
상품 상세에 SKU를 공개 표시하는 경우가 흔하다 — 오히려 고객이 CS 문의 시 상품코드로 문의할 수 있어
유리하다고 판단한다. `docs/api/product.md`(`GET /api/products`, `GET /api/products/{productId}`)
갱신을 확정 태스크로 포함한다.

### 영향 계층

- **엔티티**: `Member`(`member_code`), `Product`(`product_code`), `Payment`(`order_no`),
  `GroupBuyTeam`(`team_no`) — 코드 컬럼 추가. FK·연관관계는 변경하지 않는다.
- **DB 마이그레이션**: 컬럼 추가 + 기존 row 백필 + 이후 UNIQUE 제약 적용(4개 테이블 모두 동일 절차).
  - ⚠️ 리스크: 지금까지 이 프로젝트가 신규 NOT NULL 컬럼을 추가할 때 써온 패턴
    (`@ColumnDefault("false")`처럼 **모든 행에 같은 상수값**을 까는 방식, `member.suspended`/
    `product.hidden` 등)이 이번엔 그대로 안 통한다 — 코드는 **행마다 값이 다르므로** 상수 DEFAULT로
    채울 수 없다. "컬럼을 일단 nullable로 추가 → 기존 행을 애플리케이션 레벨로 한 건씩 백필 →
    그 다음에야 NOT NULL + UNIQUE 제약을 건다"는 순서가 필요하다는 사실만 여기 남긴다(구체 백필
    구현은 Generate 몫).
  - ⚠️ 리스크: `member.email` UNIQUE 인덱스 사례(`docs/db/member.md` 마이그레이션 메모)처럼, 배포
    (Railway) DB에 이미 데이터가 있다면 로컬 dev DB의 `ddl-auto: update`만으로는 안전하지 않을 수
    있다 — 배포 반영 시 `docs/deploy-guide.md` 절차 확인/갱신이 필요할 수 있다.
- **Admin API/DTO**: `AdminMemberResponse`에 `memberCode` 추가, `admin/products.html`이 쓰는 상품
  응답에 `productCode`가 확정 5 덕에 자동으로 실린다. `order_no`/`team_no`는 이번 라운드엔 admin
  응답에 넣지 않는다(확정 4, 확정 1).
- **공개/고객 대면 API**: `ProductSummaryResponse`(`GET /api/products`, `GET /api/products/{id}`)에
  `productCode` 추가. `GET /api/products/{productId}/teams`, `POST /api/products/{productId}/teams`
  (팀 신설 응답)에 `teamNo` 추가. 마이페이지(`docs/api/mypage.md`)의 구매자 구매내역·판매자 주문/공구
  현황 응답 — 이미 `teamId`가 원문으로 실려 있는 자리이므로 같은 위치에 `teamNo`도 추가한다.
- **Admin 프론트**: `admin-members.js`/`members.html`에 회원번호 표시, `admin-products.js`/
  `products.html`에 상품코드 표시. 기존 `search` 쿼리 파라미터(회원: 이름/아이디/이메일, 상품: 상품명/
  판매자명)에 코드 검색도 추가해야 "코드로 조회 가능"해진다.
- **고객 대면 프론트**: `product.js`(상품 상세 팀 카드)에 `teamNo` 표시, `buyer-mypage.js`/
  `seller-mypage.js`(마이페이지 주문/공구 목록)에 `teamNo` 표시. `product.html`/상품 상세 어딘가에
  `productCode` 표시(정확한 배치는 Generate 재량 — Plan은 "노출한다"까지만 정한다).
- **정책 문서**: 포맷·채번 규칙은 회원/상품/주문/공구팀 네 도메인이 공유하는 규칙이라
  `docs/policy/identifier-code.md`로 새로 분리해 각 도메인 design.md가 이를 참조하게 한다(드리프트
  방지, `docs/policy/README.md`가 말하는 cross-cutting 규칙 패턴).

## 관련 정책·문서 (신설/갱신 대상)

- `docs/policy/identifier-code.md` (신설) — 포맷·채번 규칙(위 "확정 1~5"대로).
- `docs/db/member.md`, `docs/db/product.md`, `docs/db/payment.md`, `docs/db/group_buy_team.md` —
  신규 컬럼 명세 추가.
- `docs/api/admin.md` — `GET /api/admin/members` 응답에 `memberCode`, `GET /api/admin/products`
  응답(공개 목록과 공유하는 DTO)에 `productCode`가 자연히 포함됨을 명시.
- `docs/api/product.md` — `GET /api/products`, `GET /api/products/{productId}` 응답에 `productCode`
  추가(공개 API 계약 변경이므로 명시).
- `docs/api/team.md` — `GET /api/products/{productId}/teams`, `POST /api/products/{productId}/teams`
  응답에 `teamNo` 추가.
- `docs/api/mypage.md` — 구매자 구매내역·판매자 주문/공구 현황 응답에 `teamNo` 추가.

## 태스크

- [ ] `docs/policy/identifier-code.md` 작성 (포맷·채번 규칙 SSOT)
- [ ] `docs/db/member.md`/`product.md`/`payment.md`/`group_buy_team.md`에 컬럼 명세 반영
- [ ] `docs/api/admin.md` — `memberCode`, `productCode`(공유 DTO 경유) 반영
- [ ] `docs/api/product.md` — `productCode` 반영 (공개 API)
- [ ] `docs/api/team.md` — `teamNo` 반영
- [ ] `docs/api/mypage.md` — `teamNo` 반영
- [ ] 엔티티에 코드 컬럼 추가 (`Member.memberCode`, `Product.productCode`, `Payment.orderNo`,
      `GroupBuyTeam.teamNo`)
- [ ] 신규 가입/상품등록/결제생성/팀신설 시 코드 자동 채번
- [ ] 기존 데이터 백필 배치 — 4개 테이블 전부(로컬 dev DB 우선 검증 → 배포 DB 반영 절차는
      `docs/deploy-guide.md` 확인)
- [ ] 백필 완료 후 4개 컬럼 모두 UNIQUE 제약 적용
- [ ] Admin DTO — `AdminMemberResponse.memberCode`, 상품 응답 `productCode`(공유 DTO 경유) 추가
- [ ] Admin 프론트(`admin-members.js`/`members.html`, `admin-products.js`/`products.html`)에 코드 표시
- [ ] Admin 검색에 코드 검색 지원 추가 (회원번호/상품코드)
- [ ] 공개 상품 API 응답(`ProductSummaryResponse`)에 `productCode` 반영
- [ ] 상품 상세 페이지(`product.js`)에 상품코드 표시
- [ ] 팀 관련 API(`GET /api/products/{productId}/teams`, `POST /api/products/{productId}/teams`)
      응답에 `teamNo` 반영, `product.js` 팀 카드에 `teamNo` 표시
- [ ] 마이페이지 API(구매자 구매내역, 판매자 주문/공구 현황) 응답에 `teamNo` 반영,
      `buyer-mypage.js`/`seller-mypage.js`에 표시
- [ ] (이연) `order_no`의 admin 노출(예: 환불요청 응답 확장, admin 전용 주문 목록 화면) — 다음 작업
- [ ] (이연) `team_no`의 admin 노출(admin 전용 공구팀 목록 화면) — 다음 작업

## 평가(통과) 기준

- 신규 가입 시 `member.member_code`가 자동 생성·저장된다 (NULL 없음, 포맷 일치).
- 신규 상품 등록 시 `product.product_code`가 자동 생성·저장된다.
- 신규 결제 생성 시 `payment.order_no`가 `O{접수일 yyyyMMdd}-{PK 6자리}` 포맷으로 자동 생성·저장된다.
- 신규 공구팀 신설 시 `group_buy_team.team_no`가 자동 생성·저장된다.
- 기존 회원/상품/결제/공구팀 전체 행의 코드 백필이 끝나 NULL이 하나도 없고, 이후 4개 컬럼 모두
  UNIQUE 제약이 정상 적용된다(중복 없음).
- Admin 회원 목록/상품 현황 화면에 각각 회원번호/상품코드가 표시된다.
- Admin 검색창에 회원번호/상품코드를 입력하면 해당 회원/상품이 조회된다.
- 공개 상품 목록/상세 API 응답에 `productCode`가 실리고, 상품 상세 페이지에서 확인된다.
- 상품 상세 페이지의 팀 목록 카드와 마이페이지(구매내역/주문·공구 현황)에서 `teamNo`가 확인된다.
- `order_no`/`team_no`는 이번 라운드에선 **admin 화면·API 어디에도 노출되지 않는다**(컬럼·채번까지만
  됐는지 확인 — DB에는 값이 있는데 admin 응답에 실수로 새어나가지 않았는지가 포인트).
- 기존 PK(`Long id`) 기반 API 경로·FK 연관관계는 전혀 변경되지 않아 기존 테스트가 회귀 없이 통과한다
  (`./gradlew test`).

## 리스크 / 전제

- 코드는 행마다 값이 다르므로 상수 `@ColumnDefault`로 기존 행을 채울 수 없다 — 애플리케이션 레벨
  백필이 필요하다(구현 방식은 Generate 결정).
- 배포(Railway) DB에 이미 데이터가 있다면 로컬 `ddl-auto: update` 실측만으로 안전을 보장할 수 없고,
  `member.email` UNIQUE 인덱스 사례처럼 수동 적용 절차가 필요할 수 있다.
- PK 파생 코드는 코드값 자체로 전체 회원/상품/주문/공구팀 규모를 추정할 수 있게 한다. 상품코드는
  공개 API에 실리므로 이 추정 가능성도 함께 공개된다는 사실은 인지해 둔다(위험도는 낮다고 보되,
  사실로만 기록).
- `product_code`를 공개 API에 얹으면 상품 목록 캐시(30분 TTL, `docs/policy/caching.md`)에도 그대로
  포함된다 — 상품코드는 등록 후 불변값이라 캐시 신선도 이슈는 없다고 판단되나, 사실로만 기록한다.
- **공구팀 번호 관련 전제**: `team_no`는 `GroupBuyTeam` 1건당 1개이고 `TeamParticipation`(팀원 각각의
  참여 기록)에는 번호를 붙이지 않는다 — 팀 자체를 식별하는 번호이지 참여자 개개인을 식별하는 번호가
  아니기 때문(참여자 식별은 이미 `member_code`가 담당). `TeamParticipation` 테이블/API에는 이번
  작업으로 인한 변경이 없다.
- **공구팀 상태 전이와 번호의 관계**: `team_no`는 채번(팀 신설, insert) 시점에 한 번 정해지고 이후
  `status`가 `RECRUITING → SUCCESS`/`FAILED`로 바뀌어도 값이 바뀌지 않는다(PK 파생이라 애초에 PK가
  불변이므로 자연히 불변) — 이 동작이 기대와 다르면(예: 성사된 팀만 별도 채번 규칙을 원하는 경우)
  재검토가 필요하다는 점만 전제로 남긴다.
