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
- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | targetParticipants | int | Y | 이 팀의 목표 인원(정원). **해당 상품의 `price_tier.minCount` 목록 중 정확히 하나와 일치해야 한다**(임의의 수 자유 선택 불가) |

  > 로그인 사용자가 자동으로 leader가 되고 `current_count = 1`로 생성된다.

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

  > `maxParticipants`는 요청의 `targetParticipants` 값 그대로다 — 이 값이 팀 생애 동안 불변인 정원(스냅샷)이 되고, `payment/crud`의 가격 계산(`PaymentService.resolveTeamPrice`)이 이 값을 기준으로 `price_tier`를 찾는다.
  > 팀 신설 직후 `POST /api/payments`(결제 생성)으로 이어진다 — 신설자(leader)는 결제까지 완료해야 참가가 확정된다.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `VALIDATION_FAILED` | 400 | `targetParticipants` 누락 |
  | `INVALID_TARGET_PARTICIPANTS` | 400 | `targetParticipants`가 해당 상품의 `price_tier.minCount` 목록에 존재하지 않음(범위 체크가 아니라 존재 여부 체크) |

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
  | `TOO_MANY_REQUESTS` | 429 | 같은 클라이언트(IP)가 10초 안에 20회를 초과해서 요청(트래픽 제어, `docs/dev/team/crud/design.md` 참고) |

---

## GET /api/teams/{teamId}/participants — 공구팀 참여자 목록 조회

> 참가를 고민하는 구매자가 "누가 벌써 참여했는지" 참고할 수 있게 보여준다. 실명 원문·프로필 사진·연락처·
> `memberId` 등 식별정보는 노출하지 않고, 마스킹된 이름만 내려준다(`docs/dev/team/crud/changes/` "공구팀
> 상세 — 참여자 목록 표시" 참고).

- 경로 변수: `teamId` (Long)
- 인증: **불필요**(비로그인도 조회 가능, `SecurityConfig` permitAll) — 마스킹된 이름만 노출되고
  `currentCount`처럼 이미 공개된 정보와 같은 등급으로 취급한다.

- 응답: `200 OK`
  ```json
  [
    { "displayName": "김**", "isLeader": true, "joinedAt": "2026-07-24T10:00:00" },
    { "displayName": "이*", "isLeader": false, "joinedAt": "2026-07-24T11:30:00" }
  ]
  ```

  > `displayName`은 이름 첫 글자만 노출하고 나머지는 글자 수만큼 `*`로 마스킹한 값이다(이름이 1글자면
  > `*` 하나로 전체를 가린다). 정렬은 팀장이 먼저, 이후 `joinedAt` 오름차순(참여한 순서)이다.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |

---

## 실시간 이벤트 — WebSocket/STOMP (REST 아님)

이 참가가 성공(커밋)하면, 그 팀이 속한 상품 페이지를 보고 있는 클라이언트 전원에게 갱신된 팀 상태를 실시간으로 밀어준다. 인증 불필요(이미 위 `GET /api/products/**`로 공개된 정보를 실시간으로 전달하는 것뿐).

- **핸드셰이크 엔드포인트**: `/ws-team` (SockJS 폴백 없음, 네이티브 WebSocket)
- **구독 토픽**: `/topic/products/{productId}/teams`
- **페이로드**: 위 `POST /api/teams/{teamId}/join` 응답과 동일한 형식
  ```json
  { "teamId": 3, "currentCount": 5, "maxParticipants": 10, "status": "RECRUITING" }
  ```
- **발행 시점**: `team/join` 성공(커밋) 시점뿐. 팀 신설(`POST /api/products/{productId}/teams`)은 브로드캐스트 대상이 아니다(스코프 밖, `docs/dev/team/crud/design.md` 참고).
