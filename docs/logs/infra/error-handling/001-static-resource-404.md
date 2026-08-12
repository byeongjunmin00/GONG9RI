# 001-static-resource-404 — 존재하지 않는 정적 리소스가 500으로 응답되던 버그 (로그)

## Attempt 1 — 2026-08-13 ✅ PASS

- **발견 경위**: 프로덕션 OOM 크래시 대응 중 Railway HTTP 로그를 살펴보다가 `GET /favicon.ico -> 500`을 실제로 발견함(2026-08-12 22:52). 처음엔 배포 상태 관련 경고 배지(⚠1)의 원인으로 의심했다가 무관함을 확인, 별도 버그로 분리해서 조사함.
- **원인**: `GlobalExceptionHandler`에 `@ExceptionHandler(Exception.class)` catch-all이 있는데, 존재하지 않는 정적 리소스 요청 시 스프링이 던지는 `NoResourceFoundException`을 이 핸들러가 그대로 잡아 500(`INTERNAL_SERVER_ERROR`)으로 바꿔버리고 있었다 — 원래 스프링 기본 동작은 404다. `favicon.ico`뿐 아니라 `/static/**` 경로 아래 존재하지 않는 파일 요청은 전부 이 경로를 탔을 것으로 추정(로그인한 세션이 있어야 재현됨 — 미인증 상태면 `SecurityConfig`의 `anyRequest().authenticated()`가 먼저 401로 막아서 이 버그 자체에 도달하지 않음, 실측으로 확인).
- **조치**: `GlobalExceptionHandler`에 `@ExceptionHandler(NoResourceFoundException.class)`를 `Exception.class` catch-all보다 먼저 추가해서 원래 의도한 404로 되돌림.
- **실측 검증**: 로컬 `bootRun`으로 실제 재현 — 미인증 상태로는 401(정상, 버그와 무관), **로그인 세션을 실제로 만들어서** `GET /favicon.ico` 호출하니 수정 전엔 500이 났을 상황(프로덕션과 동일 조건 재현), 수정 후 실제로 `404 NOT_FOUND`로 바뀐 것을 확인. 전체 테스트 194개 회귀 없음.
- 테스트 데이터(로컬 회원 계정) 정리 완료.
