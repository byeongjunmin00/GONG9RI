# review (상품 리뷰)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| product_id | BIGINT | NOT NULL, FK | 리뷰 대상 상품 (`product.id`) |
| member_id | BIGINT | NOT NULL, FK | 작성자 (`member.id`) |
| rating | INT | NOT NULL, 1~5 | 평점 |
| content | TEXT | NULL | 리뷰 내용 |
| created_at | DATETIME | NOT NULL | 작성일 |
| updated_at | DATETIME | NOT NULL | 마지막 수정일 |

## 인덱스 / 제약
- `idx_product` (product_id) — 상품 상세 페이지 리뷰 목록 조회용
- `uk_review_product_member` UNIQUE (product_id, member_id) — 한 회원이 같은 상품에 리뷰를 두 개 이상 남기지 못하게 함

## 관계
- product_id → product.id
- member_id → member.id

## 작성 자격
- 그 상품을 실제로 결제 완료(`payment.status = PAID`)한 이력이 있는 회원만 작성 가능(`ReviewService.create`에서 검증, 이 테이블 자체가 강제하지는 않음).
- 작성 시점에만 자격을 확인한다 — 이후 그 결제가 취소/환불돼도 이미 작성된 리뷰를 소급해서 지우지 않는다.

## 사용하는 기능
- review/create, review/list, review/update, review/delete

## 삭제 정책
- 하드 삭제 (`deleted_at` 없음). `DELETE /api/reviews/{id}`는 실제 row 삭제, 작성자 본인만 가능.
