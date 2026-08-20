# 003-logout-idempotent — 세션 만료 후 로그아웃 먹통 버그 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: 사용자 리포트("시간 좀 오래 지나면 로그아웃 눌러도 안 된다")의 원인 체인을 끝까지 추적한 뒤
  ① `POST /api/auth/logout`을 permitAll로 바꿔 멱등 연산으로 만들고 ② 프론트가 성공/실패와 무관하게
  새로고침하도록 수정.
- 원인: logout이 permitAll 목록에 없어 `anyRequest().authenticated()`에 막힘 → 세션 타임아웃(톰캣 기본
  30분) 후 화면은 로그인 헤더 그대로인데 버튼을 누르면 401 → `header-auth.js`의 catch가 `console.error`만
  하고 끝나 화면에 아무 일도 안 일어남. **`AuthController.logout()`은 이미 `getSession(false)` 후 null
  체크로 멱등하게 짜여 있었는데 인가 설정이 그 분기에 도달조차 못 하게 막고 있었다.**
- 결과: `./gradlew test` 전체 **401케이스 통과**.
- 증거(API 샘플, 프로덕션 실측):
  - 수정 전: `POST /api/auth/logout` (세션 없음) → `401 {"code":"UNAUTHORIZED"}`
  - 수정 후: `POST /api/auth/logout` (세션 없음) → `204 No Content`
  - 연속 2회 호출도 둘 다 `204`(멱등)
- 테스트: `logout_withoutSession_isStillSuccessful`, `logout_calledTwice_isIdempotent` 신규.
  **수정(permitAll)을 임시로 제거하고 돌려 두 테스트가 실제로 실패하는 것까지 역검증함.**
- 특기: 기존 `logout_unauthorized`(같은 요청에 401을 기대)를 **삭제**했다 — 그 기대값 자체가 이 버그를
  고정하고 있었기 때문. 테스트를 새 동작에 맞춰 바꾸는 건 회귀를 숨길 수 있어, 왜 기대값이 바뀌었는지를
  코드 주석과 changes 문서 양쪽에 남겼다.
