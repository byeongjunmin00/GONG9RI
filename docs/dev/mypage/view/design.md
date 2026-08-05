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
- **캐싱** (`docs/policy/caching.md`, Redis 최초 도입): `mypage/seller-revenue`(`SellerMypageService.revenue()`)를 `sellerId` 단위(`CacheConfig.SELLER_REVENUE_CACHE`, 이름 `"sellerRevenue"`)로 캐싱한다.
  - **직렬화**: 값 직렬화기로 `JacksonJsonRedisSerializer<RevenueResponse>`(타입 고정, 다형적 타이핑 아님)를 명시 설정(`CacheConfig`) — 캐시 대상 DTO(`RevenueResponse`, record)가 `Serializable`을 구현하지 않아 기본 `JdkSerializationRedisSerializer`를 쓸 수 없고, 타입 정보를 값에 저장하지 않는 범용 직렬화기(`GenericJacksonJsonRedisSerializer`)는 조회 시 역직렬화 결과가 `LinkedHashMap`으로 나와 부적합했기 때문. 이 캐시가 `RevenueResponse` 단일 타입만 다루는 현재 스코프에 한해 유효한 선택 — 다른 캐시가 추가되면 각자 타입에 맞는 직렬화기를 별도 등록해야 한다.
  - **무효화 트리거**: `PaymentService.create()` 완료 시 결제 대상 상품의 판매자(`product.getSeller().getId()`) 캐시 무효화, `TeamDeadlineService.processDeadline()`에서 환불이 실제로 발생한 경우(팀별 독립 트랜잭션 내에서 즉시) 해당 판매자 캐시 무효화. 둘 다 `CacheManager`를 직접 주입해 `cache.evict(sellerId)` 호출(파라미터가 아닌 지역변수 유래 키라 `@CacheEvict` SpEL로 표현 불가).
  - **TTL**: 10분(무효화 누락 대비 안전장치, `docs/policy/caching.md`).
  - 캐싱 로직은 Service 계층에만 있다(Controller·Repository 미개입).

## 관련 코드 위치

- `dto/{PurchaseResponse,BuyerTeamResponse,SellerProductResponse,RevenueResponse,SellerTeamResponse}.java`
- `repository/PaymentRepository.java` — `findAllByMemberIdWithProduct`, `findRevenueSummaryBySellerId`(신규 `RevenueSummaryProjection` 경유)
- `repository/TeamParticipationRepository.java` — `findAllByMemberIdWithTeamAndProduct`
- `repository/GroupBuyTeamRepository.java` — `findAllBySellerIdWithProduct`
- `repository/ProductRepository.java` — `findAllBySellerIdOrderByCreatedAtDesc`
- `entity/{TeamParticipation,Product,GroupBuyTeam}.java` — 누락돼있던 인덱스 추가
- `entity/Payment.java` — `refund()` 도메인 메서드 추가(REFUNDED 전이 테스트/향후 `payment/refund`용, 이번 스코프에서 실제로 트리거하는 API는 없음)
- `service/{BuyerMypageService,SellerMypageService,PaymentService,TeamDeadlineService}.java`
- `controller/{BuyerMypageController,SellerMypageController}.java`
- `config/CacheConfig.java` — 판매자 수익 캐시(`sellerRevenue`) TTL·값 직렬화기 설정
- 테스트: `controller/{BuyerMypageControllerTest(7케이스),SellerMypageControllerTest(11케이스)}.java` — 스코핑 테스트(구매/상품 각 1개), 매출 집계 테스트(PAID/REFUNDED 혼합 + 무결제 0건 케이스) 포함
- 테스트: `service/SellerRevenueCachingTest.java`(4케이스, `@SpringBootTest`) — 캐시 히트/무효화(결제 발생·환불) 시나리오. `config/CacheConfigTest.java`(순수 단위 테스트) — 값 직렬화기가 non-serializable `RevenueResponse`를 실제로 write/read 왕복시킬 수 있는지 검증
