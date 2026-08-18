# wishlist (찜)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| member_id | BIGINT | NOT NULL, FK | 찜한 회원(구매자, `member.id`) |
| product_id | BIGINT | NOT NULL, FK | 찜한 상품 (`product.id`) |
| created_at | DATETIME | NOT NULL | 찜한 시각 |

## 인덱스
- `idx_member` (member_id) — "내가 찜한 상품" 목록 조회용
- `uk_wishlist_member_product` (member_id, product_id) UNIQUE — 같은 상품 중복 찜 방지

## 관계
- member_id → member.id
- product_id → product.id

## 사용하는 기능
- product/wishlist(추가/제거), mypage/view(내 찜 목록)

## 삭제 정책
- 하드 삭제(`deleted_at` 없음) — 찜 해제는 실제 row 삭제. 재추가 시 새 row 생성(같은 (member_id, product_id) 조합이 다시 만들어질 수 있음, 유니크 제약은 "현재 시점에 중복 없음"만 보장).
