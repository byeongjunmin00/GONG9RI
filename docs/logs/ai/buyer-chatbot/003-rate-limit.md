# 003-rate-limit — 구매자 챗봇 API에 트래픽 제어 추가 (로그)

## Attempt 1 — 2026-08-19  ✅ PASS

- 배경: 비로그인 사용자한테도 챗봇을 열어줄지 논의하다가, `RateLimitFilter`의 규칙 목록(`team/join`,
  로그인, 이메일 재발송, 비밀번호 재설정 요청)에 챗봇 API가 아예 없다는 걸 발견함. 로그인(구매자) 게이트가
  사실상 유일한 방어선이라, 계정 하나만으로도 반복 호출로 OpenAI 비용이 계속 나갈 수 있는 갭이었음.
- 시도: `RateLimitFilter.RULES`에 `POST /api/buyer/chat/messages` 규칙 추가(1분/10회, 다른 규칙들과 같은
  IP 단위 고정 윈도우+fail-open 메커니즘 재사용).
- 검증: `BuyerChatRateLimitFilterTest` 신규 — 처음엔 SSE 컨트롤러까지 실제로 통과시켜서(`ChatClient`
  목 대체 + `request().asyncStarted()` 확인) 검증하려 했는데, MockMvc의 비동기(SseEmitter) 처리와
  얽혀 원인 불명의 "Async not started" 실패가 반복됨(디버깅에 시간 들이는 것보다 테스트 범위를
  좁히는 게 낫다고 판단). `RateLimitFilter`가 `@Order(HIGHEST_PRECEDENCE + 10)`로 Spring Security
  필터체인보다도 먼저 실행된다는 점을 이용해, **비로그인 요청**으로 재작성 — 임계값 이내는 필터를
  통과해 그 다음 인증 단계에서 401(필터 검증 목적엔 이걸로 충분), 임계값 초과는 필터 자체에서 429로
  구분. `ChatClient`/`PolicyRagService` 목 대체나 SSE 완료 대기가 전혀 필요 없어져서 테스트가 훨씬
  단순해짐.
- 결과: `./gradlew test` 전체 재실행 — **BUILD SUCCESSFUL**, 회귀 없음.
