# auth API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`
> 인증 방식: **세션 기반 (Spring Security + HttpSession)** — 로그인 성공 시 서버가 세션 쿠키(`JSESSIONID`)를 발급하고, 클라이언트는 이후 요청에 이 쿠키를 자동 포함한다. 별도 토큰 응답 없음.

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
