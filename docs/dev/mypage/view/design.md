# 구매자/판매자 마이페이지 (mypage/view) — Design

## 개요

구매자는 본인 결제 내역·공구 참여 목록·찜한 상품을, 판매자는 본인이 등록한 상품·매출 현황·상품별 공구 참여 현황을 조회한다. 대부분 쓰기 동작이 없는 순수 조회/집계 기능이라 신규 엔티티·에러코드는 없다 — 유일한 예외는 찜(`GET /api/buyer/mypage/wishlist`)인데, 그 자체의 쓰기(추가/제거)는 `product/wishlist`가 소유하고 여기선 조회만 위임받는다(`docs/dev/product/wishlist/design.md`). 목록은 페이지네이션 없이 배열로 반환한다(회원 개인 활동 기준이라 범위가 작아서 의도적 예외 — 공개 상품 카탈로그의 페이지네이션과는 다름).

## API / 인터페이스

- `GET /api/buyer/mypage/{purchases,teams,refund-requests,wishlist}`, `GET /api/seller/mypage/{products,orders,revenue,teams}` — 상세: `docs/api/mypage.md`, 찜은 `docs/api/wishlist.md`
- `POST /api/member/profile-image`, `DELETE /api/member/profile-image` — 상세: `docs/api/auth.md`

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

- **판매자 주문·배송 준비 상태 관리** (`GET /api/seller/mypage/orders`, 2026-08-21 `005-seller-order-shipment-management` 개편):
  - 판매자가 자신의 상품을 결제한 구매자 정보(이름/이메일)와 결제 금액·일시, 배송 준비 진행 단계를 한 번에 조회.
  - `preparationStatus`는 저장된 컬럼이 아니라 `SellerOrderResponse.from()`이 결제 상태(`REFUNDED`/`REFUND_PENDING`)와 공구팀 상태(`RECRUITING`/`FAILED`)로부터 그때그때 계산한 파생값(`REFUNDED`/`RECRUITING`/`FAILED`/`PREPARING`) — 다른 곳에 저장하지 않는다.
  - **`PENDING`/`FAILED` 결제는 조회 대상에서 제외**한다(`PaymentRepository.findAllBySellerIdWithProductAndMemberAndTeam`의 `WHERE` 조건) — 결제가 아직 확정되지 않았거나 실패한 건까지 포함하면 `preparationStatus` 파생 로직이 걸러내지 못하고 전부 `PREPARING`(배송 준비 중)으로 잘못 표시되기 때문(리뷰에서 발견, 2026-08-21 수정).

- **회원 프로필 사진 변경 및 삭제 기능** (`POST /api/member/profile-image`, `DELETE /api/member/profile-image`, 2026-08-21 `006-member-profile-image-upload` 개편):
  - 회원이 프로필 사진을 업로드하거나 삭제/초기화할 수 있는 API 및 마이페이지 UI 구현.
  - `Member.profileImageUrl` 필드 추가 및 `ProductImageStorage`를 통한 5MB 이하 축소 JPEG 저장 파이프라인 연동(상품 이미지와 동일한 `/uploads/{yyyy}/{MM}/{uuid}.jpg` 저장소를 공유).
  - **사진 교체/삭제 시 이전 파일을 디스크에서 함께 지운다**(`ProductImageStorage.delete()`, 2026-08-21 리뷰에서 발견해 추가) — 안 지우면 회원이 사진을 바꿀 때마다 고아 파일이 유한한 Railway 볼륨에 계속 쌓이는 문제였다. 새 파일 저장이 성공한 뒤에만 이전 파일을 지우므로, 업로드 자체가 실패(잘못된 파일 등)하면 기존 사진은 그대로 남는다. `delete()`는 `/uploads/` 접두사가 아니거나 정규화 후 저장 루트를 벗어나는 URL은 조용히 무시한다(경로 탈출 방지, `store()`와 같은 방어 원칙).
  - **세션의 SecurityContext를 즉시 갱신**한다(`AuthController.updateMe()`와 동일한 패턴) — 안 하면 재로그인 전까지 `GET /api/auth/me`가 사진 변경 전 값을 계속 돌려주는 버그였다(리뷰 중 실측으로 발견, 2026-08-21 수정).

## 관련 코드 위치

- `dto/{PurchaseResponse,BuyerTeamResponse,SellerProductResponse,RevenueResponse,SellerTeamResponse}.java`
- `dto/SellerOrderResponse.java` — 신규(005). `preparationStatus`/`preparationStatusLabel` 파생 로직 포함.
- `repository/PaymentRepository.java` — `findAllBySellerIdWithProductAndMemberAndTeam`(005, N+1 방지 패치 조인)
- `service/SellerMypageService.java` — `orders()`(005)
- `controller/SellerMypageController.java` — `GET /api/seller/mypage/orders`(005)
- `seller/mypage.html`, `js/seller-mypage.js` — 📦 주문·배송 관리 탭, `loadOrders()`(005)
- 테스트(005): `controller/SellerMypageControllerTest.java` — `orders_asSeller_returnsSellerOrdersWithBuyerInfo`, `orders_scoping_onlyOwnSalesPayments`, `orders_forbidden_buyer`, `orders_unauthorized`, `orders_excludesPendingAndFailedPayments`
- 경위(005): `docs/dev/mypage/view/changes/005-seller-order-shipment-management.md`, 실행 로그: `docs/logs/frontend/seller/005-seller-order-shipment-management.md`

- `dto/MemberResponse.java` — `profileImageUrl` 추가(006)
- `entity/Member.java` — `profileImageUrl` 필드 및 `updateProfileImage()` 도메인 메서드 추가(006).
- `controller/MemberProfileImageController.java` — 신규 프로필 사진 업로드/삭제 컨트롤러(006). 업로드/삭제 성공 후 `SecurityContextHolder`를 새 `MemberUserDetails`로 교체 + `securityContextRepository.saveContext()` 호출(세션 즉시 갱신).
- `service/MemberService.java` — `updateProfileImage()`, `deleteProfileImage()` 구현(006).
- `service/ProductImageStorage.java` — `delete(String url)` 신규(006). `/uploads/` 접두사가 아니거나 정규화 후 저장 루트를 벗어나는 URL은 조용히 무시.
- `buyer/mypage.html`, `seller/mypage.html`, `js/account-info.js` — 계정 정보 탭 [사진 변경]/[삭제] UI 및 아바타 실시간 렌더링(006).
- 테스트(006): `controller/MemberProfileImageControllerTest.java` — `uploadProfileImage_success`, `deleteProfileImage_success`, `uploadProfileImage_unauthorized`, `deleteProfileImage_unauthorized`, `uploadProfileImage_replacingDeletesOldFile`, `uploadProfileImage_refreshesSessionPrincipalImmediately`, `deleteProfileImage_refreshesSessionPrincipalImmediately`
- 테스트(006): `service/ProductImageStorageTest.java` — `delete_removesStoredFile`, `delete_ignoresInvalidOrTraversalUrls`, `delete_doesNotThrowWhenFileAlreadyGone`
- 경위(006): `docs/dev/mypage/view/changes/006-member-profile-image-upload.md`, 실행 로그: `docs/logs/frontend/mypage/006-member-profile-image-upload.md`
