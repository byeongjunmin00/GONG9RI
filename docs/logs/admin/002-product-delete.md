# 002-product-delete — 관리자 상품 삭제 (로그)

## Attempt 1 — 2026-08-21
- 배경: 사용자 요청("관리자니까 글도 삭제할 수 있게 해줘").
- 만들다 발견한 것: 기존 삭제 경로(`ProductService.delete()`)에 **참조 데이터 확인이 전혀 없었다**. `product`를 참조하는 테이블이 7개(payment, group_buy_team, review, wishlist, inquiry, price_tier, product_image)인데 price_tier와 product_image만 정리하고 있었다.
- FK 실측: `information_schema`로 조회하니 7개 모두 `DELETE_RULE = NO ACTION`. 로컬 DB에서 직접 재현 —
  ```
  ERROR 1451 (23000): Cannot delete or update a parent row:
  a foreign key constraint fails (`gong9ri_db`.`payment`,
  CONSTRAINT `FK95mdx4gcoy5aacmes6h5fxhwr` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`))
  ```
  즉 결제가 달린 상품을 지우면 DB가 거부하고, 이를 잡는 핸들러가 없으니 500이 된다.
- 정책: 회원 삭제(`MEMBER_HAS_ACTIVITY`)와 같은 결로 맞췄다.
  - 결제·공구팀·리뷰가 있으면 `PRODUCT_HAS_ACTIVITY`(409)로 거절
  - 찜·문의·가격구간·이미지는 상품과 함께 삭제(leaf 데이터이고, 상품이 사라지면 의미도 없음)
- 구현: 판매자 삭제와 **같은 내부 경로**(`deleteInternal`)를 쓴다. 관리자용으로 따로 구현하면 삭제 정책이나 캐시 무효화가 한쪽만 고쳐진다.

## Attempt 2 — 2026-08-21 (역검증에서 나온 예상 밖의 결과)
- 시도: 가드를 지우고 테스트를 돌려 500을 확인하려 함.
- 결과: **500이 아니라 204(성공)** 가 나왔다.
- 원인: **테스트가 트랜잭션 롤백**이라 `DELETE`가 DB까지 가지 않는다. FK 위반은 커밋 시점에 터지는데 커밋이 없으니 터지지 않는다.
- 대응: 이 테스트가 고정하는 건 **"409로 거절한다"는 계약**이고, FK가 막는다는 근거는 위의 DB 실측이라고 테스트 주석에 명시했다. 안 적으면 나중에 "테스트가 통과하니 가드 없어도 되겠네"로 오해한다.

## 프론트에서 잡은 것
- 삭제 버튼에 `btn-danger`를 쓸 뻔했는데 **그 클래스는 CSS에 정의돼 있지 않다** — 그대로 뒀으면 스타일 없는 맨 버튼이 됐다. 기존 회원 삭제와 같은 `btn-ghost`로 맞췄다.

### Evaluate — 2026-08-21  ✅ PASS
- 계산적 평가: `./gradlew test` 전체 통과. 신규 케이스 4개(삭제 성공 / 결제 있으면 409 / 리뷰 있으면 409 / 비관리자 403).
- 판정: PASS. 프로덕션 실동작 확인은 사용자 검증 대기.
