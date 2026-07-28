# team API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`

> **동시성 주의**: `POST /api/teams/{teamId}/join` — 여러 사용자가 동시에 참가 요청을 보낼 수 있어
> `group_buy_team.current_count` 갱신 시 동시성 제어(락)가 필수다. 구체적 전략은 Generate 단계에서 결정.

---

## GET /api/products/{productId}/teams — 상품에 대한 모집 중 팀 목록 조회

- 경로 변수: `productId` (Long)

- 응답: `200 OK`
  ```json
  [
    {
      "teamId": 3,
      "productId": 1,
      "leaderId": 7,
      "currentCount": 4,
      "maxParticipants": 10,
      "status": "RECRUITING",
      "deadline": "2026-07-31T23:59:59",
      "createdAt": "2026-07-24T10:00:00"
    }
  ]
  ```

  > 상태가 `RECRUITING`인 팀만 반환한다.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |

---

## POST /api/products/{productId}/teams — 공구팀 신설

- 경로 변수: `productId` (Long)
- 요청 body: 없음 — 로그인 사용자가 자동으로 leader가 되고 `current_count = 1`로 생성

- 응답: `201 Created`
  ```json
  {
    "teamId": 3,
    "productId": 1,
    "leaderId": 7,
    "currentCount": 1,
    "maxParticipants": 10,
    "status": "RECRUITING",
    "deadline": "2026-07-31T23:59:59",
    "createdAt": "2026-07-24T10:00:00"
  }
  ```

  > 팀 신설 직후 `POST /api/payments`(결제 생성)으로 이어진다 — 신설자(leader)는 결제까지 완료해야 참가가 확정된다.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## POST /api/teams/{teamId}/join — 공구팀 참가

- 경로 변수: `teamId` (Long)
- 요청 body: 없음 — 로그인 사용자가 해당 팀에 참가

- 응답: `200 OK`
  ```json
  {
    "teamId": 3,
    "currentCount": 5,
    "maxParticipants": 10,
    "status": "RECRUITING"
  }
  ```

  > 참가로 인해 `current_count`가 `max_participants`에 도달하면 `status`가 `SUCCESS`로 바뀔 수 있다.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |
  | `TEAM_FULL` | 409 | 정원 초과 |
  | `ALREADY_JOINED` | 409 | 이미 참가한 팀 |
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |
