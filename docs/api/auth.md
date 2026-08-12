# auth API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`
> 인증 방식: **세션 기반 (Spring Security + HttpSession)** — 로그인 성공 시 서버가 세션 쿠키(`JSESSIONID`)를 발급하고, 클라이언트는 이후 요청에 이 쿠키를 자동 포함한다. 별도 토큰 응답 없음.

## 세션 vs JWT — 세션을 선택한 이유

- **프론트가 SPA가 아니라 서버가 같이 서빙하는 정적 페이지 구조**: 이 프로젝트는 `src/main/resources/static/`의 정적 HTML/JS를 스프링부트가 API와 같은 서버에서 함께 서빙한다. 별도 도메인의 SPA가 API 서버를 호출하는 구조(대표적인 JWT 이점 — CORS 넘어 상태 없는 인증)가 아니라서, JWT가 해결해주는 "다른 오리진 간 인증 상태 공유" 문제 자체가 애초에 없다.
- **토큰 저장·갱신 복잡도 회피**: JWT를 쓰면 accessToken 만료·refreshToken 재발급·클라이언트 저장소(localStorage vs 쿠키) 등 추가로 설계·구현할 게 늘어난다. 세션은 Spring Security가 기본 제공하는 `HttpSession` + 쿠키 메커니즘을 그대로 쓰면 되고, 로그아웃도 `session.invalidate()` 한 줄로 즉시 무효화된다(JWT는 만료 전 강제 무효화가 원래 까다로움).
- **다중 서버 인스턴스 확장성은 이 프로젝트 스코프 밖**: 세션의 대표적 단점(여러 인스턴스로 스케일아웃하면 세션 공유 문제 발생, Redis 등으로 외부화 필요)은 이 프로젝트가 단일 인스턴스(Railway 배포)라 당장 해당하지 않는다. 트래픽 규모상 이 트레이드오프가 현재는 불리하지 않다고 판단했다.
- 결론: 이 프로젝트의 아키텍처(단일 서버, 서버사이드 정적 페이지 혼합)에서는 JWT의 장점을 살릴 지점이 없고, 세션이 구현·운영 복잡도 면에서 더 간단하다.

## POST /api/auth/signup — 회원가입

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | username | String | Y | 로그인 아이디 |
  | password | String | Y | 비밀번호 |
  | name | String | Y | 이름 |
  | email | String | Y | 이메일 |
  | role | String (enum) | Y | `BUYER` 또는 `SELLER` |

- 응답: `201 Created`
  ```json
  {
    "memberId": 1,
    "username": "hong1234",
    "name": "홍길동",
    "role": "BUYER"
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 유효성 실패 |
  | `DUPLICATE_USERNAME` | 409 | 이미 존재하는 아이디 |

---

## POST /api/auth/login — 로그인

> 성공 시 응답 헤더 `Set-Cookie: JSESSIONID=...`로 세션 쿠키 발급.

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | username | String | Y | 로그인 아이디 |
  | password | String | Y | 비밀번호 |

- 응답: `200 OK`
  ```json
  {
    "memberId": 1,
    "username": "hong1234",
    "name": "홍길동",
    "role": "BUYER"
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 누락 |
  | `LOGIN_FAILED` | 401 | 아이디/비밀번호 불일치 |
  | `TOO_MANY_REQUESTS` | 429 | 같은 클라이언트(IP)가 60초 안에 10회를 초과해서 요청(로그인 시도 제한 — IP 레이어, `docs/dev/auth/login/design.md` 참고) |
  | `LOGIN_ATTEMPTS_EXCEEDED` | 429 | 같은 계정이 10분 안에 5회 연속 로그인에 실패해서 잠김(로그인 시도 제한 — 계정 레이어). 맞는 비밀번호를 넣어도 잠금 기간 동안은 거절됨 |

---

## POST /api/auth/logout — 로그아웃

- 요청 body: 없음 — 서버측 세션 무효화(`HttpSession.invalidate()`)

- 응답: `204 No Content`

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `UNAUTHORIZED` | 401 | 미인증 상태 |

---

## GET /api/auth/me — 현재 로그인한 사용자 조회

> 세션 쿠키(`JSESSIONID`)로 인증 여부를 판정한다. 프론트가 페이지 로드 시 "지금 로그인 상태인지 · 어떤 역할인지"를 확인하는 용도(헤더 로그인 상태 표시 등).

- 요청 body: 없음

- 응답: `200 OK` (형식은 `signup`/`login`과 동일한 `MemberResponse`)
  ```json
  {
    "memberId": 1,
    "username": "hong1234",
    "name": "홍길동",
    "role": "BUYER"
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `UNAUTHORIZED` | 401 | 미인증 상태(세션 없음/만료) — 로그인 안 한 것으로 프론트가 판정 |
