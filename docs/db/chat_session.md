# chat_session (구매자 챗봇 대화 세션)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| buyer_id | BIGINT | NOT NULL, FK | 세션 소유 구매자(사용자 간 대화 격리 기준) |
| created_at | DATETIME | NOT NULL | 세션 생성 시각 |
| last_message_at | DATETIME | NOT NULL | 마지막 메시지 시각. 새 메시지가 올 때마다 갱신(`touch()`) |

## 인덱스
- `idx_buyer` (buyer_id) — 구매자별 세션 조회용

## 관계
- buyer_id → member.id

## 사용하는 기능
- ai/buyer-chatbot — `sessionId` 없이 요청하거나, 있어도 `last_message_at` 기준 30분 경과 시(발제 예시값) 새 세션을 만든다. 만료된 세션은 삭제하지 않고 그대로 둠(별도 배치 없음, 계산만으로 처리)

## 삭제 정책
- 하드 삭제 없음(대화 이력 보존)
