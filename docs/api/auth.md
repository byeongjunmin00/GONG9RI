# auth API

> 에러 응답 형식: `{ "code": "...", "message": "..." }` — 공통 규칙: [api/README.md](README.md)

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

- 요청 body: 없음 (세션/토큰 서버측 무효화)

- 응답: `204 No Content`

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `UNAUTHORIZED` | 401 | 미인증 상태 |
