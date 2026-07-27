# price_tier (가격 구간표)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| product_id | BIGINT | NOT NULL, FK | 어떤 상품의 구간인지 |
| min_count | INT | NOT NULL | 이 가격이 적용되는 최소 참여 인원 |
| price | INT | NOT NULL | 해당 인원대의 1인당 가격 |
| created_at | DATETIME | NOT NULL | 등록일 |

## 인덱스
- `idx_product` (product_id) — 상품 상세 조회 시 구간표 전체 조회용

## 관계
- product_id → product.id

## 사용하는 기능
- product/register (구간표 같이 등록), product/detail (구간표 표시), payment/create (구간별 가격 계산)

## 삭제 정책
- 하드 삭제. 상품 수정(`PUT /api/products/{id}`) 시 기존 구간표 전체 삭제 후 재삽입.
