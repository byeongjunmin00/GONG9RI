# 찜(위시리스트) — Design

## 개요

구매자가 관심 상품을 찜해두고 마이페이지에서 모아볼 수 있는 기능. 와디즈/텀블벅 등 참고 사이트의 하트 아이콘 패턴을 따른다.

## API / 인터페이스

`docs/api/wishlist.md`가 원천. 핵심:
- `POST`/`DELETE /api/products/{productId}/wishlist` — 추가/제거, **둘 다 멱등**(이미 그 상태여도 에러 없이 성공). 하트 아이콘 토글 UI가 "지금 찜 상태인지"를 매번 먼저 조회하지 않고 그냥 반대 동작을 호출해도 안전하게 만들기 위함.
- `GET /api/buyer/mypage/wishlist` — 내 찜 목록(상품 요약 정보 포함, `bestPrice`는 `ProductService.list()`와 동일하게 `PriceTierRepository.findBestPricesByProductIds`로 계산).

## 데이터 모델

`docs/db/wishlist.md` 참고. `wishlist(member_id, product_id)` 유니크 제약. 하드 삭제(제거=row 삭제).

## 규칙 / 검증

- **구매자 전용**(`requireBuyer`) — 결제·팀 참가와 동일한 역할 제약. 판매자 계정은 403.
- **메인 페이지 카드의 하트 상태는 캐시된 상품 목록 응답에 넣지 않는다** — 상품 목록(`PRODUCT_LIST_CACHE`)은 모든 사용자가 공유하는 캐시인데, "이 상품을 내가 찜했는지"는 회원마다 다른 값이라 여기 넣으면 다른 사용자의 찜 상태가 섞여 보이는 실제 버그가 된다(진행바처럼 "느려도 되는 공용 사실"이 아니라 "한 사람만의 진실"이라 캐시 우회가 아니라 애초에 그 응답에 포함시키면 안 됨). 대신 프론트(`js/main.js`)가 로그인 확인 후 `GET /api/buyer/mypage/wishlist`를 별도로 한 번 불러와 이미 렌더링된 카드의 하트를 클라이언트에서 채운다.
- 상품 상세 페이지(`product.html`)에는 아직 하트를 추가하지 않았다(스코프 밖, 메인 페이지 카드 + 마이페이지 목록만) — 필요해지면 이후 확장.

## 관련 코드

`entity/Wishlist.java`, `repository/WishlistRepository(Custom/Impl).java`, `dto/WishlistItemResponse.java`, `service/WishlistService.java`, `controller/WishlistController.java`, `service/BuyerMypageService.wishlist()`(조회 위임), `static/js/main.js`(카드 하트), `static/js/buyer-mypage.js`(찜 목록 섹션).
