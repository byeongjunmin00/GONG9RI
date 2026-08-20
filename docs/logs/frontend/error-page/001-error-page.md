# 001-error-page — 브라우저용 에러 페이지 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: 브라우저 탐색(`Accept: text/html`)일 때만 404/401을 `/error.html`로 보내고, 프로그램 호출은
  기존 JSON을 그대로 유지하도록 `ApiAuthenticationEntryPoint`와 `GlobalExceptionHandler`에 분기 추가.
- 원인: permitAll 목록이 확장자·접두사 기반(`/*.html`, `/css/**`)이라 **확장자 없는 경로**가 어디에도
  안 걸려 `anyRequest().authenticated()`에 막혔다 → 핸들러 도달 전 거절이라 404가 될 기회조차 없었고,
  사용자는 브라우저에서 날 JSON + "로그인이 필요합니다"라는 **사실과 다른 안내**를 봤다.
- 결과: `./gradlew test` 전체 **407케이스 통과**.
- 증거(API 샘플, 프로덕션 실측):
  ```
  # 브라우저 (Accept: text/html,...)
  GET /nonexistent-path   → 302 Location: /error.html
  GET /오타페이지.html      → 302 Location: /error.html
  GET /product            → 302 Location: /error.html

  # 프론트 fetch (Accept: */*) — 변화 없음
  GET /api/buyer/mypage/notifications → 401 {"success":false,"code":"UNAUTHORIZED",...}
  GET /오타페이지.html                  → 404 {"success":false,"code":"NOT_FOUND",...}
  ```
- 테스트: `ErrorPageResponseTest` 6케이스. **그중 3건이 회귀 방지** — 프론트 전체가 에러 응답의 `code`를
  파싱해 분기하므로(로그인 배너·결제 실패 안내) 형태가 바뀌면 한꺼번에 망가진다.
- 특기: 인가 규칙(기본 차단)은 **일부러 건드리지 않았다**. "`/api/**` 아니면 permitAll"로 바꾸면 404가
  나오지만 기본 정책이 차단→허용으로 뒤집혀, 나중에 non-API 엔드포인트를 추가하면 자동 공개되는 함정이
  된다. 404와 401을 같은 페이지로 합친 것도 경로 존재 여부를 노출하지 않기 위함.
