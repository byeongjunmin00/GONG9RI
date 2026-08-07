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
- **판매자 수익 집계** (`docs/db/seller_revenue_summary.md`, 2026-08-06 upsert 전환, `docs/dev/mypage/view/changes/004-upsert-fix.md`): `mypage/seller-revenue`(`SellerMypageService.revenue()`)는 캐싱하지 않고 `seller_revenue_summary` 테이블을 **순수 조회**만 한다 — 조회 시점에 행을 만드는 쓰기는 없다.
  - **갱신 시점 = 결제/환불 시점**: `PaymentService.create()` → `SellerRevenueSummaryRepository.incrementPaid`가 MySQL `INSERT ... ON DUPLICATE KEY UPDATE`로 동작하는 **upsert**다 — 그 판매자의 요약 행이 없으면 그 결제 값으로 새로 만들고, 있으면 원자적으로 증가시킨다. 그래서 판매자의 **첫 결제가 들어오는 순간** 요약 행이 정확한 값으로 생기고, 조회 여부와 무관해진다(동시에 같은 판매자에게 첫 결제가 여러 건 들어와도 유실 없이 정확히 합산됨, `UNIQUE(seller_id)` 충돌 시 MySQL이 행 락을 걸고 UPDATE로 전환). `TeamDeadlineService.processDeadline()` → `applyRefund`(환불 시 감소, 여전히 조건부 UPDATE)는 결제 시점에 이미 요약 행이 있다는 전제 위에서 동작하며, 그 전제가 깨지면(백필 안 된 판매자에게 환불이 먼저 들어오는 경우) 0 rows affected를 WARN 로그로 드러낸다.
  - **이전 방식(폐기)**: 요약 행을 판매자가 자기 수익 페이지를 조회할 때 지연 부트스트랩으로 만들던 방식은, `incrementPaid`가 "행이 있으면만 증가"하는 조건부 UPDATE였던 것과 시점이 어긋나 경쟁 상태(조회 전 결제가 조용히 무시됨)를 낳았다 — 이번 정정으로 요약 행 생성 시점을 "조회"에서 "결제"로 옮겨 경쟁 상태 자체를 없앴다.
  - **기존 데이터 백필(1회성)**: 이번 upsert 전환 이전부터 있던 결제 이력이 있는데 아직 요약 행이 없는 판매자는 `SellerRevenueSummaryBackfillService`(대상 탐색은 `PaymentRepository.findDistinctSellerIdsWithPayments` + 기존 집계 쿼리 `findRevenueSummaryBySellerId`, 둘 다 삭제하지 않고 유지)로 채운다. 조회마다 실행되면 안 되므로(예전 지연 부트스트랩과 같은 문제 재발) `SellerRevenueSummaryBackfillRunner`(`ApplicationRunner`, 기본 비활성, `app.backfill.seller-revenue-summary=true`로만 opt-in 실행)를 통해서만, 배포 시점에 딱 한 번 트리거한다.
  - Redis 캐싱(`@Cacheable`/`CacheConfig.SELLER_REVENUE_CACHE`)은 이미 제거된 상태(003) — product 목록/상세 캐싱과는 무관.

## 관련 코드 위치

- `dto/{PurchaseResponse,BuyerTeamResponse,SellerProductResponse,RevenueResponse,SellerTeamResponse}.java` — `RevenueResponse.empty()`(요약 행 없을 때 순수 0 응답) 추가. 호출부가 전혀 없던 `RevenueResponse.from(RevenueSummaryProjection)` 오버로드는 죽은 코드로 확인되어 제거(Evaluate에서 grep으로 무호출 확인 후 정리)
- `entity/SellerRevenueSummary.java`, `repository/SellerRevenueSummaryRepository.java`(`incrementPaid`: 네이티브 `INSERT ... ON DUPLICATE KEY UPDATE` upsert, `applyRefund`: 조건부 UPDATE) — 신규
- `repository/PaymentRepository.java` — `findAllByMemberIdWithProduct`, `findRevenueSummaryBySellerId`(`RevenueSummaryProjection` 경유, 백필/드리프트 검증용으로 유지), `findDistinctSellerIdsWithPayments`(백필 대상 탐색용, 신규)
- `service/SellerRevenueSummaryBackfillService.java`(1회성 백필, 판매자 1명당 트랜잭션 분리), `batch/SellerRevenueSummaryBackfillRunner.java`(`ApplicationRunner`, 기본 비활성, `app.backfill.seller-revenue-summary=true`로만 opt-in 실행) — 신규
- `repository/TeamParticipationRepository.java` — `findAllByMemberIdWithTeamAndProduct`
- `repository/GroupBuyTeamRepository.java` — `findAllBySellerIdWithProduct`
- `repository/ProductRepository.java` — `findAllBySellerIdOrderByCreatedAtDesc`
- `entity/{TeamParticipation,Product,GroupBuyTeam}.java` — 누락돼있던 인덱스 추가
- `entity/Payment.java` — `refund()` 도메인 메서드 추가(REFUNDED 전이 테스트/향후 `payment/refund`용, 이번 스코프에서 실제로 트리거하는 API는 없음)
- `service/{BuyerMypageService,SellerMypageService,PaymentService,TeamDeadlineService}.java` — `SellerMypageService.revenue()`는 쓰기 없는 순수 조회(클래스 기본 `@Transactional(readOnly = true)` 그대로 사용), `TeamDeadlineService.processDeadline()`은 `applyRefund` 리턴값이 0이면 WARN 로그
- `controller/{BuyerMypageController,SellerMypageController}.java`
- `config/CacheConfig.java` — 판매자 수익 캐시 관련 코드 제거(product 목록/상세 캐시만 남음)
- 테스트: `controller/{BuyerMypageControllerTest(7케이스),SellerMypageControllerTest(11케이스)}.java` — 스코핑 테스트(구매/상품 각 1개), 매출 집계 테스트(요약 행 직접 seed해 GET 응답 wiring 검증 + 무결제 0건 케이스) 포함
- 테스트: `service/SellerRevenueSummaryTest.java`(결제 시 upsert로 요약 행 생성·증가, 환불 시 감소, 결제 이력 없는 판매자의 순수 0 조회, 1회성 백필, 대량 더미 데이터 드리프트 검증), `service/SellerRevenueSummaryConcurrencyTest.java`(요약 행이 아예 없는 상태에서 동시에 여러 "첫 결제"가 들어와도 정확히 합산되는지 검증, `@SpringBootTest` 논트랜잭션 멀티스레드). `config/CacheConfigTest.java`(순수 단위 테스트) — product 목록/상세 값 직렬화기 검증만 유지
