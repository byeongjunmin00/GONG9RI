# identifier-code — Design

## 개요

회원(`Member`)·상품(`Product`)·주문/결제(`Payment`)·공구팀(`GroupBuyTeam`) 4개 엔티티에 기존
PK(`Long id`)와 별도로 사람이 읽는 식별 코드 컬럼을 둔다. 전부 **PK 파생**이다 — 별도 채번
카운터 테이블 없이, 저장 직후(PK 확정 직후) 같은 트랜잭션에서 PK를 코드 문자열로 변환해 저장한다.
기존 FK·연관관계는 전혀 바뀌지 않는다.

신규 생성 경로(회원가입/카카오 신규가입, 상품등록, 결제생성, 팀신설)는 저장 직후 즉시 채번한다.
이 컬럼들이 생기기 이전에 만들어진 기존 행은 `IdentifierCodeBackfillService` +
`IdentifierCodeBackfillRunner`(opt-in, `app.backfill.identifier-code=true`)로 애플리케이션 레벨
백필한다.

**현재 4개 컬럼 모두 nullable이고 UNIQUE 제약은 아직 걸지 않았다.** "컬럼 nullable 추가 →
백필 → NOT NULL/UNIQUE 제약"의 1단계까지만 이번 라운드 스코프이며, 2단계(제약 적용)는
`docs/deploy-guide.md`의 별도 배포 절차로 남긴다.

## API / 인터페이스

- `GET /api/admin/members` — 응답에 `memberCode`, `search` 파라미터로 회원번호 검색 가능
  (`docs/api/admin.md`).
- `GET /api/admin/products` — 공개 목록과 공유하는 `ProductSummaryResponse` 경유로 `productCode`
  자동 포함, `search` 파라미터로 상품코드 검색 가능 (`docs/api/admin.md`).
- `GET /api/products`, `GET /api/products/{productId}` — 응답에 `productCode` (`docs/api/product.md`).
- `GET /api/products/{productId}/teams`, `POST /api/products/{productId}/teams` — 응답에 `teamNo`
  (`docs/api/team.md`).
- 마이페이지(`docs/api/mypage.md`) 구매자 구매내역·구매자 공구 참여 목록·판매자 주문/공구 현황 —
  `teamId`가 원문 노출되는 자리마다 같은 위치에 `teamNo`도 노출.
- `order_no`/`team_no`는 admin API 어디에도 노출하지 않는다(admin 전용 주문/공구팀 목록 화면이
  아직 없음, 다음 작업으로 이연).
- Admin 프론트: `admin/members.html`+`admin-members.js`(회원번호 표시), `admin/products.html`+
  `admin-products.js`(상품코드 표시).
- 고객 대면 프론트: `product.html`+`product.js`(상품코드 배지, 팀 카드 `teamNo`),
  `buyer-mypage.js`/`seller-mypage.js`(`teamNo` 표시).

## 데이터 모델

| 테이블 | 컬럼 | 상세 |
|---|---|---|
| `member` | `member_code` | `docs/db/member.md` |
| `product` | `product_code` | `docs/db/product.md` |
| `payment` | `order_no` | `docs/db/payment.md` |
| `group_buy_team` | `team_no` | `docs/db/group_buy_team.md` |

포맷·채번·마이그레이션 순서·노출 범위 규칙의 SSOT는 `docs/policy/identifier-code.md`.

## 규칙 / 검증

- 포맷 SSOT 코드: `com.gong9ri.gong9ri.common.identifier.IdentifierCodeFormatter`(정적 유틸리티).
- 채번 호출 지점: `MemberService.signup()`/`findOrCreateByKakao()`, `ProductService.register()`,
  `PaymentService.create()`, `TeamService.create()` — 각각 저장 직후 같은 트랜잭션에서 `assignXxx(...)`
  호출.
- 백필: `IdentifierCodeBackfillService`(행 1개당 `@Transactional` 메서드, 자가호출 경로에서도 안전하게
  반영되도록 `assignXxx(...)` 직후 `repository.save(entity)`를 명시적으로 호출) +
  `IdentifierCodeBackfillRunner`(기본 비활성, 배포 시 opt-in 실행, 조회 경로에서는 호출 안 함).
- 검색: `MemberRepositoryImpl`/`ProductRepositoryImpl`의 QueryDSL 조건에 각각
  `memberCode`/`productCode` `containsIgnoreCase` 추가(백필 전 NULL 행이 있어도 매치 안 함으로
  안전하게 평가됨).
- 관련 정책: `docs/policy/identifier-code.md`.
