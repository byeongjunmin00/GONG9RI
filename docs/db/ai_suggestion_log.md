# ai_suggestion_log (AI 상품등록 도우미 호출 로그)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| seller_id | BIGINT | NOT NULL, FK | 요청한 판매자 |
| category | VARCHAR(20) | NOT NULL | 사용된 프롬프트 카테고리(`FOOD`/`GENERAL`) |
| input_text | TEXT | NOT NULL | 판매자가 입력한 원문 |
| suggested_name | VARCHAR(100) | NULL | AI 제안 상품명(실패 시 NULL) |
| suggested_description | TEXT | NULL | AI 제안 설명(실패 시 NULL) |
| suggested_base_price | INT | NULL | AI 제안 기본가(실패 시 NULL) |
| suggested_max_participants | INT | NULL | AI 제안 최대인원(실패 시 NULL) |
| prompt_tokens | INT | NULL | 요청 토큰 수(성공 시에만) |
| completion_tokens | INT | NULL | 응답 토큰 수(성공 시에만) |
| total_tokens | INT | NULL | 총 토큰 수(성공 시에만) |
| latency_ms | BIGINT | NULL | 응답 지연(ms), 성공/실패 모두 기록 |
| success | BOOLEAN | NOT NULL | 성공 여부 |
| error_message | VARCHAR(500) | NULL | 실패 사유(실패 시에만) |
| created_at | DATETIME | NOT NULL | 생성 시각 |

## 인덱스
- `idx_seller` (seller_id) — 판매자별 호출 이력 조회용

## 관계
- seller_id → member.id

## 사용하는 기능
- ai/product-suggestion — 성공/실패 모두 기록(비용 인식·토큰 사용량 추적). 로그 저장은 `AiSuggestionLogRecorder`(별도 빈, `REQUIRES_NEW`)가 전담 — `suggest()`의 트랜잭션과 분리해서, 실패 시 재던지는 예외가 방금 저장한 실패 로그까지 롤백시키는 걸 방지

## 삭제 정책
- 하드 삭제 없음(호출 이력·비용 감사 목적)
