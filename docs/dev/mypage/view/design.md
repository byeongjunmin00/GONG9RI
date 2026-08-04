# 구매자/판매자 마이페이지 (mypage/view) — Design

## 개요

구매자는 본인 결제 내역·공구 참여 목록을, 판매자는 본인이 등록한 상품·매출 현황·상품별 공구 참여 현황을 조회한다. 쓰기 동작이 없는 순수 조회/집계 기능이라 신규 엔티티·에러코드는 없다. 목록은 페이지네이션 없이 배열로 반환한다(회원 개인 활동 기준이라 범위가 작아서 의도적 예외 — 공개 상품 카탈로그의 페이지네이션과는 다름).

## API / 인터페이스

- `GET /api/buyer/mypage/{purchases,teams}`, `GET /api/seller/mypage/{products,revenue,teams}` — 상세: `docs/api/mypage.md`

## 데이터 모델

- 신규 테이블 없음. 기존 `payment`, `team_participation`, `group_buy_team`, `product` 위에서 조회/집계만 수행 — 상세: 각 `docs/db/*.md`
- 이번에 `docs/db/team_participation.md`(`idx_member`)와 `docs/db/product.md`(`idx_seller`)에 이미 문서화돼 있었지만 실제 엔티티에 빠져있던 인덱스를 추가했고, 겸사겸사 `docs/db/group_buy_team.md`의 `idx_product_status`/`idx_status_deadline`도 같은 이유로 함께 추가함. 로컬 MySQL에서 `SHOW INDEX`로 5개 인덱스 전부 실제 반영 확인함.

## 규칙 / 검증

- 구매자 엔드포인트는 `Role.BUYER`, 판매자 엔드포인트는 `Role.SELLER`만 가능(반대 역할 시도 시 `403 FORBIDDEN`)
- **스코핑이 핵심**: buyer 엔드포인트는 `member.id` 기준, seller 엔드포인트는 `product.seller.id` 기준으로 본인 데이터만 반환 — 각 축마다 스코핑 테스트로 검증(타인 데이터 안 보이는지)
- `GET /api/buyer/mypage/teams`는 상태 필터 없이 `RECRUITING`/`SUCCESS`/`FAILED` 전체 반환, 프론트가 `status` 필드로 성사/미성사 구분
- `GET /api/seller/mypage/revenue`는 `PAID` 결제만 `totalRevenue`에 합산(`REFUNDED`는 금액에서 제외, 건수만 별도 카운트) — 조건부 SUM/COUNT 한 쿼리로 처리
- N+1 방지(`docs/code-convention.md` 표 그대로): purchases(payment→product), buyer/teams(team_participation→group_buy_team→product), seller/teams(group_buy_team→product) 전부 fetch join. seller/products는 cross-entity 데이터가 없어 fetch join 불필요
- `docs/api/mypage.md` 필드명 정규화: buyer/teams 응답의 `teamStatus`를 `status`로 통일(seller/teams, 기존 `TeamResponse`와 일치)
- 캐싱 후보(고도화 단계, MVP 아님): `mypage/seller-revenue`는 `docs/policy/caching.md`에 이미 캐싱 대상으로 명시돼 있음

## 관련 코드 위치

- `dto/{PurchaseResponse,BuyerTeamResponse,SellerProductResponse,RevenueResponse,SellerTeamResponse}.java`
- `repository/PaymentRepository.java` — `findAllByMemberIdWithProduct`, `findRevenueSummaryBySellerId`(신규 `RevenueSummaryProjection` 경유)
- `repository/TeamParticipationRepository.java` — `findAllByMemberIdWithTeamAndProduct`
- `repository/GroupBuyTeamRepository.java` — `findAllBySellerIdWithProduct`
- `repository/ProductRepository.java` — `findAllBySellerIdOrderByCreatedAtDesc`
- `entity/{TeamParticipation,Product,GroupBuyTeam}.java` — 누락돼있던 인덱스 추가
- `entity/Payment.java` — `refund()` 도메인 메서드 추가(REFUNDED 전이 테스트/향후 `payment/refund`용, 이번 스코프에서 실제로 트리거하는 API는 없음)
- `service/{BuyerMypageService,SellerMypageService}.java`
- `controller/{BuyerMypageController,SellerMypageController}.java`
- 테스트: `controller/{BuyerMypageControllerTest(7케이스),SellerMypageControllerTest(11케이스)}.java` — 스코핑 테스트(구매/상품 각 1개), 매출 집계 테스트(PAID/REFUNDED 혼합 + 무결제 0건 케이스) 포함
