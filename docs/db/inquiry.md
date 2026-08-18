# inquiry (상품 문의)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| product_id | BIGINT | NOT NULL, FK | 문의 대상 상품 (`product.id`) |
| member_id | BIGINT | NOT NULL, FK | 작성자(질문자, `member.id`) |
| content | TEXT | NOT NULL | 문의 내용 |
| answer_content | TEXT | NULL | 판매자 답변 내용. `NULL`이면 미답변 |
| answered_by | BIGINT | NULL, FK | 답변한 판매자 (`member.id`). 항상 `product.seller_id`와 같아야 한다(서비스 레이어에서 검증, 이 테이블 자체가 강제하지는 않음) |
| answered_at | DATETIME | NULL | 답변 등록 시각. 미답변인 동안은 `NULL` |
| created_at | DATETIME | NOT NULL | 문의 작성일 |
| updated_at | DATETIME | NOT NULL | 문의 내용 마지막 수정일(답변 등록/수정 시각과는 별개) |

## 인덱스 / 제약
- `idx_product` (product_id) — 상품 상세 페이지 문의 목록 조회용

> `member_id`(작성자) 전용 인덱스는 이번 스코프에서 "내 문의 목록" 조회 기능을 만들지 않으므로 추가하지
> 않는다(review의 `idx_product`와 동일한 근거 — 실제 조회 조건이 되는 컬럼에만 부여).

## 관계
- product_id → product.id
- member_id → member.id (작성자)
- answered_by → member.id (답변자, nullable)

## 작성 자격
- **리뷰와 달리 결제(구매) 이력을 요구하지 않는다.** 구매 전 질문(배송, 옵션 등)이 문의의 핵심 용도이기
  때문 — 로그인한 회원이면 role과 무관하게 작성 가능하다.

## 답변 자격 / 규칙
- 그 상품을 등록한 판매자(`product.seller`) 본인만 답변을 등록·수정·삭제할 수 있다(`InquiryService`에서
  검증).
- 문의 1건당 답변은 0개 또는 1개다(스레드형 다중 답변 없음).
- **답변이 등록된 문의는 작성자가 내용을 수정/삭제할 수 없다** — 질문-답변의 정합성과 판매자 답변의
  신뢰성을 보존하기 위함(사용자 확인 필요 항목, 계획 문서 참고).
- 답변 삭제는 문의 자체를 지우지 않고 `answer_content`/`answered_by`/`answered_at`만 `NULL`로 되돌린다
  (다시 미답변 상태로 전환, 질문은 유지).

## 사용하는 기능
- inquiry/crud (신규 개념) — 문의 작성/목록/수정/삭제, 판매자 답변 등록/수정/삭제

## 삭제 정책
- 하드 삭제 (`deleted_at` 없음). `DELETE /api/inquiries/{id}`는 실제 row 삭제, 답변이 없는 문의에 한해
  작성자 본인만 가능(위 "답변 자격/규칙" 참고).
