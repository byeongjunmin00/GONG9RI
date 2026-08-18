# 찜(위시리스트) 기능 추가

대상: product/wishlist
담당: 민병준

## 배경 / 요구

친구가 보내준 와디즈/텀블벅 참고 화면에서 반복적으로 나온 찜(하트) 기능. "욕심나는 것들 싹 다 해보자"는 사용자 요청으로 착수(순서는 위임받아 직접 정함).

## 설계

`docs/dev/product/wishlist/design.md` 참고 — 추가/제거 멱등 처리, 구매자 전용, 카드 하트 상태는 공유 캐시에 넣지 않고 프론트에서 별도 조회로 채우는 방식.

## 태스크

- [x] `Wishlist` 엔티티(member_id+product_id 유니크)
- [x] `WishlistRepository`(멱등 add/remove용 exists/delete) + Custom/Impl(fetch join 목록 조회)
- [x] `WishlistService`(add/remove 멱등, myWishlist)
- [x] `WishlistController`(POST/DELETE `/api/products/{id}/wishlist`)
- [x] `BuyerMypageController`/`Service`에 `GET /api/buyer/mypage/wishlist` 추가
- [x] 메인 페이지 카드에 하트 아이콘(로그인 상태 연동, 클릭 시 토글)
- [x] 구매자 마이페이지에 "찜한 상품" 섹션(목록+찜 해제)
- [x] 테스트: 추가/멱등/제거/멱등/판매자 403/비로그인 401/상품 없음 404
- [x] `docs/api/wishlist.md`, `docs/db/wishlist.md` 신규 작성

## 평가(통과) 기준

- `./gradlew test` 통과
- 로컬 실서버로 하트 토글, 마이페이지 목록 반영, 멱등 동작 실측 확인
