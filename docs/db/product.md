# product (상품)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| seller_id | BIGINT | NOT NULL, FK | 등록한 판매자 (`member.id`) |
| name | VARCHAR(100) | NOT NULL | 상품명 |
| description | TEXT | NULL | 상품 설명 |
| base_price | INT | NOT NULL | 정가 (1인 구매 시 가격) |
| max_participants | INT | NOT NULL | 이 상품에 허용되는 팀 인원 상한(참고값). 각 `price_tier.min_count`는 이 값을 넘을 수 없다(현재는 프론트 가드레일로만 검증, 서버 강제는 없음). **실제 팀 정원은 이 값이 아니라 구매자가 팀 신설 시 고른 `price_tier.min_count`로 결정된다**(`group_buy_team.max_participants`, `team/create` 참고) |
| image_url | VARCHAR(500) | NULL | 상품 이미지 URL (단순 문자열, 갤러리 없음) |
| auto_refund_on_cancel | BOOLEAN | NOT NULL, default false | 참여 취소(`team/leave`)로 자동 생성되는 환불 요청을 판매자 승인 없이 즉시 처리할지 여부(상품 단위 설정). 켜져 있어도 솔로 구매 직접 환불 요청에는 영향 없음 — 그건 항상 판매자 승인이 필요하다. 기존 row가 있는 테이블에 추가한 NOT NULL 컬럼이라 `@ColumnDefault("false")`로 DB DEFAULT를 둬 안전하게 마이그레이션한다(`member.email_verified`와 동일 패턴) |
| category | VARCHAR(20) | NOT NULL, default 'ETC' | 메인 페이지 카테고리 필터용(product/category). `FOOD`/`LIVING`/`BEAUTY`/`FASHION`/`DIGITAL`/`ETC` 중 하나(`@Enumerated(STRING)`). 등록/수정 시 필수 선택. 기존 row가 있는 테이블에 추가한 NOT NULL 컬럼이라 `@ColumnDefault("'ETC'")`로 안전하게 마이그레이션 — 기존 상품은 전부 `ETC`로 시작하고 재분류는 판매자가 상품 수정 폼에서 직접 한다 |
| open_at | DATETIME | NULL | 오픈예정(product/product-launch) 시각. `NULL`이면 이미 공개된 상품(등록 즉시 노출, 기존 하위 호환 기본값이라 `@ColumnDefault` 불필요 — NULL 자체가 "이미 공개" 의미). 미래 시각이면 그 전까지 혼자구매·신규 팀 신설이 `PRODUCT_NOT_YET_OPEN`으로 거절된다(목록/상세 노출은 그대로 유지, 구매만 차단) |
| created_at | DATETIME | NOT NULL | 등록일 |
| updated_at | DATETIME | NOT NULL | 마지막 수정일 |

## 인덱스
- `idx_seller` (seller_id) — 판매자 마이페이지 "내가 등록한 상품 목록" 조회용

## 관계
- seller_id → member.id

## 사용하는 기능
- product/register, product/list, product/detail, product/update, product/delete, mypage/seller-products,
  team/leave(`auto_refund_on_cancel` 참조), refund/request, product/product-launch(`open_at` 참조,
  payment/crud·team/crud의 생성 진입점에서 참조)

## 삭제 정책
- 하드 삭제 (`deleted_at` 없음). `DELETE /api/products/{id}`는 실제 row 삭제.
  - 주의: 이미 결제/팀이 연결된 상품 삭제 시 FK 정합성 이슈 — Generate 단계에서 "진행 중 팀 있으면 삭제 금지" 등 제약 추가 검토 필요
