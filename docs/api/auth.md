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
  | `DUPLICATE_EMAIL` | 409 | 이미 사용 중인 이메일(로그인 고도화 2단계 — 비밀번호 재설정이 이메일로 계정을 유일하게 찾아야 해서 추가된 제약, `docs/dev/auth/email-verification/design.md` 참고) |

> 가입 성공 시 세션은 발급되지 않는다(즉시 로그인되지 않음) — 이메일 인증 링크를 클릭해야 로그인 가능(아래 `EMAIL_NOT_VERIFIED` 참고). 인증 메일은 가입 트랜잭션 커밋 후 비동기로 발송된다.

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
  | `EMAIL_NOT_VERIFIED` | 403 | 비밀번호는 맞지만 이메일 인증을 안 한 계정(로그인 고도화 2단계, `docs/dev/auth/email-verification/design.md` 참고) — 세션이 발급되지 않는다 |

---

## GET /api/auth/verify-email — 이메일 인증 확인

> 이메일 안의 링크를 브라우저로 직접 클릭해서 들어오는 요청 — JSON이 아니라 안내 HTML(`text/html`)을 응답한다.

- 쿼리 파라미터: `token`(String, Y) — 가입 시 발송된 인증 메일 안의 링크에 포함됨(24시간 유효, 1회성)

- 응답: `200 OK` — 인증 완료 안내 HTML(로그인 페이지 링크 포함)

- 에러:
  | 상황 | HTTP | 설명 |
  |------|------|------|
  | 토큰이 유효하지 않거나 만료됨/이미 사용됨 | 400 | 안내 HTML로 재발송 요청 유도 |

---

## POST /api/auth/verify-email/resend — 인증 메일 재발송

> 계정 존재 여부·인증 상태와 무관하게 항상 동일한 성공 응답을 반환한다(계정 존재 여부 비노출).

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | username | String | Y | 로그인 아이디 |

- 응답: `200 OK`, `data: null`

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 누락 |
  | `TOO_MANY_REQUESTS` | 429 | 같은 클라이언트(IP)가 5분 안에 3회를 초과해서 요청 |

---

## POST /api/auth/password/reset-request — 비밀번호 재설정 요청

> 이메일 존재 여부와 무관하게 항상 동일한 성공 응답을 반환한다(계정 존재 여부 비노출).

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | email | String | Y | 가입 시 등록한 이메일 |

- 응답: `200 OK`, `data: null` — 등록된 이메일이면 재설정 링크가 담긴 메일 발송(30분 유효, 1회성)

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 누락/이메일 형식 오류 |
  | `TOO_MANY_REQUESTS` | 429 | 같은 클라이언트(IP)가 5분 안에 3회를 초과해서 요청 |

---

## POST /api/auth/password/reset — 비밀번호 재설정 확정

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | token | String | Y | 재설정 메일 링크의 토큰 |
  | newPassword | String | Y | 새 비밀번호 |

- 응답: `200 OK`, `data: null` — 변경 후 이전 비밀번호는 더 이상 사용할 수 없음. (기존 로그인 세션은 강제 무효화되지 않음 — 알려진 한계, `docs/dev/auth/password-reset/design.md` 참고)

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 누락 |
  | `INVALID_OR_EXPIRED_TOKEN` | 400 | 토큰이 유효하지 않거나 만료됨/이미 사용됨 |

---

## GET /api/auth/kakao/login — 카카오 로그인 시작

> 사람(브라우저)이 링크를 클릭해서 들어오는 요청 — JSON이 아니라 카카오 인가 페이지로 **302 리다이렉트**한다. `docs/dev/auth/social-login/design.md` 참고.

- 요청: 쿼리 파라미터 `role`(선택, `BUYER` | `SELLER`) — **신규 가입일 때만** 쓰인다(이미 연동된 계정으로 로그인하는 경우 role은 항상 무시되고 기존 role 그대로 로그인됨). 값이 없거나 유효하지 않으면 세션에 저장하지 않고, 신규 가입 시엔 `BUYER`로 폴백한다(2026-08-14부터 — `docs/dev/auth/social-login/design.md` "role 불일치 안내" 참고). `role`을 명시적으로 보냈는지 여부가 아래 콜백의 `kakaoRoleMismatch` 안내 신호 발생 조건에 영향을 준다.
- 응답: `302 Found`, `Location: https://kauth.kakao.com/oauth/authorize?...` — CSRF 방지용 `state`와(명시된 경우) `role`을 세션에 저장한다.

---

## GET /api/auth/kakao/callback — 카카오 로그인 콜백

> 카카오가 직접 호출하는 리다이렉트 대상 — 사람이 직접 호출하는 API가 아니다. 성공/실패 모두 JSON이 아니라 **302 리다이렉트**로만 응답한다.

- 쿼리 파라미터: `code`, `state`(카카오가 자동으로 실어서 리다이렉트함)
- 응답:
  | 상황 | 리다이렉트 위치 |
  |------|------|
  | 성공(신규 가입, 또는 기존 연동 계정 로그인 + role 일치/미명시) | `302 Found` → `/` (세션 발급됨) |
  | 성공 + role 불일치 안내(`role`을 명시적으로 보냈는데 기존 연동 계정의 role과 다름 — 로그인은 기존 role 그대로 진행됨, 2026-08-14 추가) | `302 Found` → `/?kakaoRoleMismatch=BUYER\|SELLER`(실제 로그인된 role) — `index.html`이 안내 배너 표시 |
  | 실패(state 불일치, 토큰 교환/사용자 정보 조회 실패, 이메일이 이미 다른 계정에서 사용 중, 합성 username(`kakao_{id}`)이 이미 일반 회원가입으로 존재) | `302 Found` → `/login.html?error=kakao` |

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
