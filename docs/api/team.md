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
      "teamNo": "T0000003",
      "productId": 1,
      "leaderId": 7,
      "currentCount": 4,
      "maxParticipants": 10,
      "status": "RECRUITING",
      "deadline": "2026-07-31T23:59:59",
      "createdAt": "2026-07-24T10:00:00",
      "joinedByCurrentMember": false
    }
  ]
  ```

  > `teamNo`(admin-identifier-codes, 2026-08-22 추가): `docs/policy/identifier-code.md` 포맷
  > (`T0000001`, PK 파생). admin이 아니라 이 화면(상품 상세 팀 카드) 같은 고객 대면 자리에 노출한다.
  > 이 컬럼이 생기기 이전 기존 팀은 백필 전까지 `null`일 수 있다.
  >
  > 상태가 `RECRUITING`인 팀만 반환한다.
  > `joinedByCurrentMember`는 **이 요청을 보낸 로그인 사용자 자신이** 그 팀의 현재 참여자인지
  > 여부다(다른 참여자의 신원은 여전히 비공개 — `GET /api/teams/{teamId}/participants`의 마스킹
  > 원칙과 동일). 비로그인 요청이면 항상 `false`. 프론트(`product.js`)가 이 값으로 "참가하기" 대신
  > "참여 취소" 버튼을 보여줄지 판단한다.

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

- 응답: `201 Created` — `teamNo`(admin-identifier-codes, 2026-08-22 추가)도 함께 실린다(방금 채번된
  값이라 항상 값이 있다).
  ```json
  {
    "teamId": 3,
    "teamNo": "T0000003",
    "productId": 1,
    "leaderId": 7,
    "currentCount": 1,
    "maxParticipants": 10,
    "status": "RECRUITING",
    "deadline": "2026-07-31T23:59:59",
    "createdAt": "2026-07-24T10:00:00",
    "joinedByCurrentMember": true
  }
  ```

  > `maxParticipants`는 요청의 `targetParticipants` 값 그대로다 — 이 값이 팀 생애 동안 불변인 정원(스냅샷)이 되고, `payment/crud`의 가격 계산(`PaymentService.resolveTeamPrice`)이 이 값을 기준으로 `price_tier`를 찾는다.
  > 팀 신설 직후 프론트가 자동으로 결제 페이지(`checkout.html`)로 이동시킨다 — 신설자(leader)는
  > 결제까지 완료해야 참가가 확정된 채로 유지된다. 10분 안에 결제가 `PAID`로 확정되지 않으면
  > 자동으로 참가가 취소된다(아래 "미결제 참여 자동 만료" 참고).

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `PRODUCT_NOT_YET_OPEN` | 409 | 오픈예정 시각이 아직 안 지난 상품(product/product-launch) |
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
  > 참가 직후 프론트가 자동으로 결제 페이지(`checkout.html`)로 이동시킨다 — 10분 안에 결제가 `PAID`로
  > 확정되지 않으면 자동으로 참가가 취소된다(아래 "미결제 참여 자동 만료" 참고).

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

## POST /api/teams/{teamId}/leave — 공구팀 참여 취소

`join()`과 대칭적인 API다. 모집 중인(`RECRUITING`) 팀에서만 취소할 수 있다 — 팀이 이미 정원을 채워
`SUCCESS`로 전환된 뒤에는 어떤 경로로도 취소할 수 없다(사용자 확인, `docs/dev/ongoing/
team-leave-and-refund-request.md`의 악용 방지 근거 참고).

- 경로 변수: `teamId` (Long)
- 요청 body: 없음 — 로그인한 구매자 본인이 그 팀에서 나간다.

- 응답: `200 OK` (`POST /api/teams/{teamId}/join`과 동일한 응답 형식)
  ```json
  { "teamId": 3, "currentCount": 1, "maxParticipants": 5, "status": "RECRUITING" }
  ```

  > 취소 즉시 `currentCount`가 감소해 자리가 반환된다(다른 사람이 그 자리에 바로 참가 가능). 취소한
  > 사람이 리더였다면 그다음 최초 참가자에게 리더가 승계된다(`leaderId`는 이 응답에 포함되지 않지만
  > 팀 목록/상세 조회에서 확인 가능).
  >
  > **마지막 남은 참여자가 참여를 취소하면 팀 상태가 `FAILED`로 자동 전환된다**(공구 무산, `currentCount` 0).
  > 취소한 사람이 리더였다면 팀 상태가 `FAILED`가 되었으므로 리더 승계는 발생하지 않는다.
  >
  > 취소한 사람이 그 팀에 대해 `PAID` 결제를 갖고 있으면, 이 응답과 같은 트랜잭션 안에서 환불 요청
  > (`docs/api/refund.md`)이 자동 생성된다(사유 없음 — "참여 취소"가 곧 사유). 상품별
  > "참여 취소 시 자동 환불" 설정이 켜져 있으면 승인 절차 없이 즉시 처리되고, 꺼져 있으면 판매자
  > 승인/거절을 기다린다.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |
  | `TEAM_NOT_RECRUITING` | 409 | 팀이 이미 `SUCCESS`/`FAILED`로 전환됨 — 참여 취소 불가 |
  | `FORBIDDEN` | 403 | 그 팀의 참여자가 아님, 또는 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

  > **프론트 사용처**: 결제 페이지(`checkout.html`)의 "취소하기"가 `teamId`가 있는 경우 이동 전에
  > 이 API를 호출해 예약을 즉시 반환한다. 상품 상세 페이지(`product.html`)의 팀 목록에서도
  > `joinedByCurrentMember`가 `true`인 팀에는 "참가하기" 대신 이 API를 호출하는 "참여 취소" 버튼을
  > 보여준다(마이페이지의 참여 취소와 동일 엔드포인트).

---

## 미결제 참여 자동 만료 (사용자 대면 API 아님, 내부 스케줄러)

팀 참가/신설(`join`/`create`)은 결제 완료 여부와 무관하게 자리를 즉시 반영한다("예약 후 유예" 모델).
결제 페이지로 자동 이동은 시키지만, 사용자가 결제를 끝까지 완료하지 않고 이탈(창 닫기 등)할 수 있으므로
자리가 영구히 묶이지 않도록 서버가 보장한다.

- **규칙**: 참가/신설 시점(`TeamParticipation.joinedAt`) 기준 **10분** 안에 그 참여에 연결된 `Payment`가
  `PAID`로 확정되지 않으면, 그 참여를 `POST /api/teams/{teamId}/leave`와 동일한 효과로 자동 취소한다
  (정원 감소, 필요 시 리더 승계, 마지막 참여자면 팀 `FAILED` 전환). 결제가 애초에 `PAID`가 아니므로
  환불 요청은 생성되지 않는다.
- **트리거**: 사용자 요청이 아니라 내부 스케줄러다 — 엔드포인트 없음.
- **경계**: 팀 마감(`docs/policy/refund-trigger.md`, `team/deadline-check`)과는 별개 개념이다 — 마감은
  "팀이 정원을 못 채운 채 마감 시각이 지난 경우"를, 이 자동 만료는 "개별 참여자가 결제를 안 끝낸 경우"를
  다룬다.

---

## 실시간 이벤트 — WebSocket/STOMP (REST 아님)

이 참가가 성공(커밋)하면, 그 팀이 속한 상품 페이지를 보고 있는 클라이언트 전원에게 갱신된 팀 상태를 실시간으로 밀어준다. 인증 불필요(이미 위 `GET /api/products/**`로 공개된 정보를 실시간으로 전달하는 것뿐).

- **핸드셰이크 엔드포인트**: `/ws-team` (SockJS 폴백 없음, 네이티브 WebSocket)
- **구독 토픽**: `/topic/products/{productId}/teams`
- **페이로드**: 위 `POST /api/teams/{teamId}/join` 응답과 동일한 형식
  ```json
  { "teamId": 3, "currentCount": 5, "maxParticipants": 10, "status": "RECRUITING" }
  ```
- **발행 시점**: `team/join` 또는 `team/leave` 성공(커밋) 시점. 팀 신설(`POST /api/products/{productId}/teams`)은 브로드캐스트 대상이 아니다(스코프 밖, `docs/dev/team/crud/design.md` 참고).
