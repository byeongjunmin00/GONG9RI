# product (상품)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| seller_id | BIGINT | NOT NULL, FK | 등록한 판매자 (`member.id`) |
| name | VARCHAR(100) | NOT NULL | 상품명 |
| description | TEXT | NULL | 상품 설명 |
| base_price | INT | NOT NULL | 정가 (1인 구매 시 가격) |
| max_participants | INT | NOT NULL | 팀 하나당 최대 인원(N) |
| image_url | VARCHAR(500) | NULL | 상품 이미지 URL (단순 문자열, 갤러리 없음) |
| created_at | DATETIME | NOT NULL | 등록일 |
| updated_at | DATETIME | NOT NULL | 마지막 수정일 |

## 인덱스
- `idx_seller` (seller_id) — 판매자 마이페이지 "내가 등록한 상품 목록" 조회용

## 관계
- seller_id → member.id

## 사용하는 기능
- product/register, product/list, product/detail, product/update, product/delete, mypage/seller-products

## 삭제 정책
- 하드 삭제 (`deleted_at` 없음). `DELETE /api/products/{id}`는 실제 row 삭제.
  - 주의: 이미 결제/팀이 연결된 상품 삭제 시 FK 정합성 이슈 — Generate 단계에서 "진행 중 팀 있으면 삭제 금지" 등 제약 추가 검토 필요
