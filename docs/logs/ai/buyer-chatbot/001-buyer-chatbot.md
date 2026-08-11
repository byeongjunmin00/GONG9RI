# 001-buyer-chatbot — 구매자 챗봇 (로그)

## Attempt 1 — 2026-08-11 ✅ PASS

- 시도: Tool Calling(`@Tool` 2개), 장애격리(Fallback 2가지+환각방어), SSE 스트리밍+멀티턴, 비용인식 대시보드 전체 구현. Spring AI 2.0.0에서 `SseEmitter`(Spring MVC 표준, webflux 불필요)와 `ChatClient.stream().chatResponse()`(`Flux<ChatResponse>`) 조합이 실제로 동작하는지, `DefaultToolExecutionExceptionProcessor`가 예외를 자연어로 변환하는 기본 동작인지 등을 jar 디컴파일로 미리 확인 후 구현.
- 목 기반 테스트(`ChatbotToolsTest` 3케이스, `ChatLogRecorderTest` 7케이스, `BuyerChatServiceTest` 6케이스, `BuyerChatControllerTest` 7케이스, 총 23케이스) 전부 첫 시도에 통과 — 실제 OpenAI 호출 없이 `ChatClient.Builder`를 `@MockitoBean`으로 대체해 스트리밍 청크 합치기, 타임아웃/RateLimit/기타 예외의 폴백 분류, N턴 윈도잉(12개 중 최근 10개만 시간순), 세션 소유권/만료 판정, 챗봇 실패 후에도 상품 저장(핵심 서비스 예시)이 정상 동작하는지까지 전부 검증.
- `./gradlew build` 전체 131케이스(기존 108 + 신규 23) 통과, 회귀 없음.

## Attempt 1 (실제 OpenAI 호출 검증) — 2026-08-11 ✅ PASS

로컬에서 실제 판매자 계정으로 상품("제주 감귤 5kg", 25,000원) 등록 + 구매자 계정으로 그 상품에 공구팀 신설(1/10명 참여) 후, 같은 세션으로 4턴 실제 대화.

1. **Tool 1(상품 검색) 실호출 확인**: "감귤 관련 상품 있어? 가격도 알려줘" → "제주 감귤 5kg... 25,000원"으로 정확히 답변. `searchProducts` Tool이 실제로 호출돼 방금 등록한 실제 상품명·가격을 그대로 반영함(지어낸 값이 아님).
2. **Tool 2(내 참여 조회) 실호출 확인, 같은 세션으로 멀티턴**: "내가 지금 참여중인 공구팀 있어? 몇 명 모였는지도 알려줘" → "제주 감귤 5kg... 1명 참여... 최대 10명... 마감일은 2026년 8월 18일"로 정확히 답변(실제 DB 값과 일치). 세션ID가 이전 턴과 동일(35)하게 유지되며 두 번째 턴이 첫 번째 턴의 맥락(같은 세션) 위에서 자연스럽게 이어짐.
3. **환각 방어 확인**: 같은 세션에서 "오늘 서울 날씨 어때? 그리고 이 공구팀 판매자 전화번호 뭐야?" → "서울 날씨와 같은 GONG9RI 공동구매 서비스와 무관한 질문에는 답변할 수 없습니다. 또한, 판매자 전화번호와 관련된 정보도 제공할 수 없습니다"로 정확히 거절. 서비스 무관 질문(날씨)과 Tool로 확인 안 되는 정보(전화번호) 둘 다 지어내지 않고 정직하게 거절함 — 시스템 프롬프트의 grounding 지시가 실제로 작동함을 확인.
4. **토큰/지연 실측**: `chat_interaction_log`에 3턴 전부 성공 기록(`promptTokens` 316~443, `completionTokens` 45~84, `latencyMs` 1396~3139). `GET /api/chat/stats` → `{"model":"gpt-4o-mini","callCount":3,"totalTokens":1389,"p95LatencyMs":3139,"errorRate":0.0}`, `GET /api/buyer/chat/sessions/35/usage` → `{"sessionId":35,"totalTokens":1389}` 둘 다 실제 누적값과 정확히 일치.
5. **스트리밍 확인**: 모든 응답이 SSE `message` 이벤트로 어절 단위(OpenAI 스트리밍 토큰 단위)로 여러 번 나뉘어 도착, 마지막 청크에만 `usage`가 채워짐을 실측으로 확인(design.md에 반영). `session`/`done` 이벤트도 정확한 시점에 정확히 1회씩 도착.

## 참고 — 장애 시나리오는 실제 OpenAI 장애를 재현할 수 없어 목 테스트로 검증

발제가 요구하는 "장애 시나리오 테스트"는 실제 API 호출로는 타임아웃/RateLimit/5xx를 임의로 재현할 수 없으므로, `BuyerChatServiceTest`에서 `Flux.error(new TimeoutException(...))`/`Flux.error(mock(RateLimitException.class))`/`Flux.error(new RuntimeException(...))`를 직접 흘려보내 각각 `ChatErrorType.TIMEOUT`/`RATE_LIMIT`/`OTHER`로 정확히 분류·기록되는지 검증함(3케이스 전부 통과). 실제 15초 타임아웃을 기다리는 대신 예외 자체를 주입해서 분류 로직만 빠르게 검증하는 방식 — Reactor의 실제 타임아웃 메커니즘 자체는 라이브러리 표준 기능이라 재검증 대상이 아님.

## 참고 — API 크레딧 사용

이번 로그의 실제 OpenAI 호출은 총 3회(전부 성공). 팀원 API 키를 빌려 쓰는 만큼, 계획된 4가지 검증 시나리오(상품검색/내참여조회/환각방어/멀티턴)를 최소 호출(같은 세션 3턴)로 전부 확인하도록 설계함.
