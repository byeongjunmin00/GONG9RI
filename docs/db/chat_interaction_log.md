# chat_interaction_log (구매자 챗봇 호출 로그)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| session_id | BIGINT | NOT NULL, FK | 소속 세션 |
| model | VARCHAR(50) | NOT NULL | 호출한 모델명(예: `gpt-4o-mini`) |
| latency_ms | BIGINT | NOT NULL | 응답 지연(ms), 성공/실패 모두 기록 |
| success | BOOLEAN | NOT NULL | 성공 여부 |
| error_type | VARCHAR(20) | NULL | 실패 유형(`TIMEOUT`/`RATE_LIMIT`/`SERVER_ERROR`/`OTHER`, 성공 시 NULL) |
| prompt_tokens | INT | NULL | 요청 토큰 수(성공 시에만) |
| completion_tokens | INT | NULL | 응답 토큰 수(성공 시에만) |
| total_tokens | INT | NULL | 총 토큰 수(성공 시에만) |
| created_at | DATETIME | NOT NULL | 생성 시각 |

## 인덱스
- `idx_session` (session_id) — 세션별 토큰 누적량 조회용
- `idx_model` (model) — 모델별 대시보드(누적 토큰·P95 지연·에러율) 집계용

## 관계
- session_id → chat_session.id

## 사용하는 기능
- ai/buyer-chatbot — LLM 호출 1회(턴 1개)마다 성공/실패 관계없이 1행. 대화 내용(`chat_message`)과 역할 분리 — 토큰 컬럼은 여기만 있음. `GET /api/chat/stats`(모델별 누적토큰·P95지연·에러율), `GET /api/buyer/chat/sessions/{id}/usage`(세션별 누적토큰) 집계에 사용. 로그 저장은 `ChatLogRecorder`(별도 빈, `REQUIRES_NEW`)가 전담

## 삭제 정책
- 하드 삭제 없음(호출 이력·비용 감사 목적)
