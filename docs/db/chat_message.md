# chat_message (구매자 챗봇 대화 메시지)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| session_id | BIGINT | NOT NULL, FK | 소속 세션 |
| role | VARCHAR(20) | NOT NULL | 발화자(`USER`/`ASSISTANT`) |
| content | TEXT | NOT NULL | 메시지 본문 |
| created_at | DATETIME | NOT NULL | 생성 시각 |

## 인덱스
- `idx_session` (session_id) — 세션별 대화 이력 조회·N턴 윈도잉용

## 관계
- session_id → chat_session.id

## 사용하는 기능
- ai/buyer-chatbot — 대화 내용만 담당(토큰 사용량 등 지표는 `chat_interaction_log`가 별도로 담당, 역할 분리). 프롬프트 조립 시 최근 10개(5턴)만 가져와서 사용(N턴 윈도우, 근거: 상품 검색·참여 조회는 Tool Calling으로 매번 실시간 재조회하므로 오래된 맥락 의존도가 낮음). LLM 실패 시 USER 메시지만 저장되고 ASSISTANT 메시지는 저장 안 됨(실제 응답이 아니므로)

## 삭제 정책
- 하드 삭제 없음(대화 이력 보존)
