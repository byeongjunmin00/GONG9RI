# chat API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`
>
> **`POST /buyer/chat/messages`만 예외** — 이 엔드포인트는 응답을 JSON 한 번에 반환하지 않고 `text/event-stream`(SSE)으로 스트리밍한다. 아래 별도 표기 참고. 상세 설계: `docs/dev/ai/buyer-chatbot/design.md`.

## POST /api/buyer/chat/messages — 챗봇에게 메시지 전송 (구매자 전용, SSE 스트리밍)

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | sessionId | Long | N | 이어갈 세션 ID. 없거나 만료(마지막 대화로부터 30분 경과)된 경우 새 세션 생성 |
  | content | String | Y | 사용자 메시지 |

- 응답: `200 OK`, `Content-Type: text/event-stream` — SSE 이벤트 스트림
  | 이벤트명 | data | 설명 |
  |------|------|------|
  | `session` | 세션 ID(문자열) | 스트림 시작 시 1회. `sessionId`를 안 보냈거나 만료돼서 새로 만들어졌으면 그 새 ID |
  | `message` | 텍스트 조각 | 어시스턴트 응답이 생성되는 대로 여러 번. LLM 장애 시 폴백 안내 문구도 이 이벤트로 옴(클라이언트는 성공/실패를 구분해서 특별 처리할 필요 없음) |
  | `done` | (빈 값) | 스트림 종료 1회 |

  ```
  event:session
  data:35

  event:message
  data:안녕하세요,

  event:message
  data: 감귤 상품을 찾아드릴게요.

  event:done
  data:
  ```

- 에러(스트림 시작 전, 일반 JSON 에러 응답 — 세션 소유권/존재 여부는 SSE가 아니라 여기서 걸러짐):
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | `content` 누락 등 |
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 / 타인의 `sessionId`로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `CHAT_SESSION_NOT_FOUND` | 404 | 존재하지 않는 `sessionId` |

  > LLM 자체 장애(타임아웃/RateLimit/5xx)는 HTTP 에러가 아니라 위 `message` 이벤트로 자연어 안내가 온다 — "지금 응답이 지연되고 있어요..." 또는 "일시적으로 AI 상담이 어려워요..." 두 가지 중 하나.

---

## GET /api/buyer/chat/sessions/{sessionId}/messages — 대화 이력 조회 (구매자 본인만)

- 경로 변수: `sessionId` (Long)

- 응답: `200 OK`
  ```json
  [
    { "messageId": 51, "role": "USER", "content": "감귤 관련 상품 있어?", "createdAt": "2026-08-11T14:43:00" },
    { "messageId": 52, "role": "ASSISTANT", "content": "제주 감귤 5kg가 있습니다...", "createdAt": "2026-08-11T14:43:03" }
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `CHAT_SESSION_NOT_FOUND` | 404 | 존재하지 않는 세션 |
  | `FORBIDDEN` | 403 | 본인 세션이 아니거나 판매자 계정 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## GET /api/buyer/chat/sessions/{sessionId}/usage — 세션별 토큰 누적량 (구매자 본인만)

- 경로 변수: `sessionId` (Long)

- 응답: `200 OK`
  ```json
  { "sessionId": 35, "totalTokens": 1389 }
  ```

- 에러: 위 대화 이력 조회와 동일(`CHAT_SESSION_NOT_FOUND`/`FORBIDDEN`/`UNAUTHORIZED`)

---

## GET /api/chat/stats — 모델별 누적 토큰·P95 응답지연·에러율 대시보드 (로그인만 하면 조회 가능)

- 응답: `200 OK`
  ```json
  [
    { "model": "gpt-4o-mini", "callCount": 3, "totalTokens": 1389, "p95LatencyMs": 3139, "errorRate": 0.0 }
  ]
  ```
  `errorRate`는 0.0~1.0 비율(0.05 = 5%).

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `UNAUTHORIZED` | 401 | 미인증 |
