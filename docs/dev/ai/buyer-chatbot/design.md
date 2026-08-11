# 구매자 챗봇 (ai/buyer-chatbot) — Design

## 개요

구매자가 자연어로 채팅하면 AI가 필요시 백엔드 API(Tool)를 직접 호출해서 실시간 데이터를 근거로 답한다. 응답은 SSE로 토큰 단위 스트리밍되고, 대화는 세션 단위로 저장되어 멀티턴이 가능하다. 발제 AI 필수2(Tool Calling), 필수3(장애격리·비용인식), 도전(SSE 스트리밍+멀티턴)을 이 기능 하나로 묶어 구현한다. AI 도전 나머지 하나(RAG)는 전용운 담당(별도 진행) — 발제 요구사항에 "RAG+Tool Calling 결합"이 있어 나중에 이 서비스에 결합될 수 있으므로, 프롬프트/컨텍스트 조립을 한 곳에 모아두되 지금 RAG 훅은 만들지 않는다.

## API / 인터페이스

- `POST /api/buyer/chat/messages`(SSE), `GET /api/buyer/chat/sessions/{id}/messages`, `GET /api/buyer/chat/sessions/{id}/usage`, `GET /api/chat/stats` — 상세: `docs/api/chat.md`

## 데이터 모델

- `chat_session`(buyer FK, createdAt, lastMessageAt) — 세션 만료(30분, 발제 예시값)는 별도 배치 없이 `lastMessageAt`과 현재 시각 비교로만 계산. 만료된 세션은 삭제하지 않고 그대로 두고 새 세션을 만든다.
- `chat_message`(session FK, role USER/ASSISTANT, content) — 대화 내용만 담당. 토큰 컬럼은 여기 없음(아래 로그와 역할 분리).
- `chat_interaction_log`(session FK, model, latencyMs, success, errorType nullable, promptTokens/completionTokens/totalTokens) — LLM 호출 1회(턴 1개)마다 성공/실패 관계없이 1행. 토큰·응답시간·에러율 로깅(발제 AI 필수3)과 대시보드 집계용.

## Tool Calling (발제 AI 필수2)

Spring AI 2.0.0 `@Tool`(`org.springframework.ai.tool.annotation.Tool`) + `ChatClient.tools(Object...)`. `ChatbotTools`는 스프링 빈이 아니라 요청마다 로그인한 구매자 ID를 캡처해서 `new`로 생성한다 — 싱글톤 빈으로 두면 동시 요청 간 buyerId가 섞이기 때문.

- **Tool 1** `searchProducts(keyword)` — `ProductRepository.findTop10ByNameContainingIgnoreCase`(신규 derived query).
- **Tool 2** `getMyTeamParticipations()` — `TeamParticipationRepository.findAllByMemberIdWithTeamAndProduct`(기존 쿼리, `BuyerMypageService.teams()`와 동일) 재사용.
- **Tool 호출 실패 시 자연어 안내**: Spring AI의 `DefaultToolExecutionExceptionProcessor`가 기본적으로 `@Tool` 메서드의 예외를 그대로 죽이지 않고 메시지를 LLM에 tool 응답으로 돌려줘서 자연어로 안내하게 하는 구조(jar 디컴파일로 `alwaysThrow` 옵션 존재 확인, 이 프로젝트 스코프에서는 두 Tool 다 예외를 던지는 경로가 없어 실제 재현 테스트는 안 함 — 필요해지면 추가).

## 장애격리 (발제 AI 필수3)

- **Fallback 1 — 타임아웃**: `.stream().chatResponse()`(`Flux<ChatResponse>`)에 `.timeout(Duration.ofSeconds(15))`. **15초는 실측 근거 없는 초기값**(`refund-trigger` 1분 주기와 같은 성격) — 추후 실제 지연 데이터로 재검토 필요.
- **Fallback 2 — API 장애**: `com.openai.errors.RateLimitException`/`InternalServerException`/그 외 예외를 잡아 별도 안내. 에러 유형은 `ChatErrorType`(TIMEOUT/RATE_LIMIT/SERVER_ERROR/OTHER) 4종으로 로그에 구분 기록(대시보드 breakdown용)하되, 사용자에게 보여주는 문구는 "타임아웃"과 "그 외 API 장애" 2가지로 충분하다고 판단(발제 "최소 2가지" 요구 충족).
- **핵심 서비스 무영향**: 챗봇 Tool은 전부 읽기 전용(상품 검색, 내 참여 조회) — 주문/결제 상태를 바꾸지 않음. `BuyerChatServiceTest.streamChat_failureDoesNotAffectCoreService`로 챗봇 실패 후에도 상품 저장(핵심 서비스 예시)이 정상 동작함을 직접 검증.
- **환각 방어**: 시스템 프롬프트에 "모르는 정보는 반드시 Tool을 호출해서 확인하고, 확인 안 된 사실을 지어내지 마라. 확실하지 않으면 모른다고 답하라" + "GONG9RI 공동구매 서비스와 무관한 질문에는 답하지 말고 안내만 하라"를 명시. 실제 호출로 (1) 서비스 무관 질문(날씨) 거절, (2) Tool로 확인 안 되는 정보(판매자 전화번호) "제공할 수 없다"고 답하는 것 둘 다 확인함(`docs/logs/ai/buyer-chatbot/001-buyer-chatbot.md`).

## SSE 스트리밍 + 멀티턴 (발제 도전)

- **스트리밍**: `ChatClient...stream().chatResponse()`(`Flux<ChatResponse>`)를 구독해서 `SseEmitter`로 텍스트 청크 전달. `SseEmitter`는 Spring MVC 표준 기능이라 webflux 없이도 동작함(실제 확인, `reactor-core`는 Spring AI가 이미 전이 의존성으로 가짐). SSE 이벤트는 `session`(세션ID, 스트림 시작 시 1회) / `message`(텍스트 청크, 반복) / `done`(스트림 종료, 1회) 3종 — 실패 시 폴백 안내문도 `message` 이벤트로 동일하게 보내서 클라이언트가 성공/실패를 특별취급 안 해도 되게 함.
- **세션 재사용/만료**: `ChatLogRecorder.getOrCreateSession` — `sessionId`가 없으면 새로 만들고, 있으면 소유자 확인(`FORBIDDEN`) 후 `lastMessageAt` 기준 30분 경과 여부로 재사용/신규 생성을 결정.
- **최근 N턴 윈도우(N=10, 최근 5턴)**: `ChatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc` → 시간순으로 뒤집어 `.messages(...)`에 포함. 근거: 상품 검색·내 참여 조회는 Tool Calling으로 매번 실시간 재조회하므로 오래된 대화 맥락 의존도가 낮음. 이 초기값도 실측 후 조정 여지 있음.
- **사용자 간 격리**: `ChatSession.buyer` FK로 스코핑, mypage와 동일한 본인 소유 확인 패턴.
- **토큰 사용량 추출**: 스트리밍 응답은 청크마다 `ChatResponseMetadata.getUsage()`가 항상 채워지지 않고 실측 결과 마지막 청크에만 채워짐(OpenAI 스트리밍 특성) — 스트림 도중 `Usage`가 non-null로 오는 마지막 값을 계속 갱신해두었다가 완료 시점에 사용.

## 트랜잭션 — DB 쓰기는 전부 `ChatLogRecorder`의 REQUIRES_NEW

SSE 스트리밍은 컨트롤러 요청 스레드가 즉시 `SseEmitter`를 반환한 뒤, 실제 완료/실패는 Reactor 스케줄러 스레드의 콜백에서 비동기로 처리된다 — 그 시점엔 기댈 "앰비언트 트랜잭션" 자체가 없다. 그래서 세션 생성/조회, 메시지 저장, 로그 저장을 전부 `ChatLogRecorder`(별도 빈)의 `@Transactional(REQUIRES_NEW)` 메서드로 통일했다. 이는 `AiSuggestionLogRecorder`(AI 필수1)와 전용운의 `NotificationService`에서 이미 두 번 겪은 "실패를 잡아 로그 저장 후 예외를 다시 던지면 롤백에 로그까지 같이 사라지는" 문제의 세 번째 적용 사례이기도 하다.

- **성공 시**: `ChatMessage`(USER) + `ChatMessage`(ASSISTANT) + `ChatInteractionLog.success` 저장 + 세션 `touch()`.
- **실패 시**: `ChatMessage`(USER)만 저장(어시스턴트 메시지는 실제 응답이 아니므로 저장 안 함) + `ChatInteractionLog.failure` 저장 + 세션 `touch()`.

## 비용인식 — 대시보드

`GET /api/chat/stats`(모델별 누적 토큰·P95 응답지연·에러율), `GET /api/buyer/chat/sessions/{id}/usage`(세션별 누적 토큰). 이 프로젝트 데이터 규모에서는 DB 퍼센타일 함수 대신, 로그를 모델별/세션별로 모아 애플리케이션 레벨에서 정렬 후 계산하는 방식을 택함(단순함 우선). 별도 프론트 대시보드 UI는 스코프 밖 — 전용운이 필요하면 이 JSON을 그대로 붙여 쓸 수 있음.

## 관련 코드 위치

- `entity/{ChatSession,ChatMessage,ChatRole,ChatInteractionLog,ChatErrorType}.java`
- `repository/{ChatSessionRepository,ChatMessageRepository,ChatInteractionLogRepository}.java`, `ProductRepository.findTop10ByNameContainingIgnoreCase`(신규)
- `dto/{ChatMessageRequest,ChatMessageResponse,ChatSessionUsageResponse,ChatStatsResponse,ProductSearchResult}.java` — `BuyerTeamResponse`(기존 mypage DTO)를 Tool 2 응답에 재사용
- `service/{BuyerChatService,ChatLogRecorder,ChatbotTools}.java` — `ChatbotTools`는 요청마다 `new`
- `controller/BuyerChatController.java`
- `common/exception/ErrorCode.java` — `CHAT_SESSION_NOT_FOUND` 추가
- 테스트: `service/ChatbotToolsTest`(Tool 메서드 직접 단위 테스트 — mock ChatClient로는 Spring AI의 실제 tool 실행 메커니즘을 안 타므로), `service/ChatLogRecorderTest`(세션 생성/재사용/만료/소유권, 성공·실패 턴 기록), `service/BuyerChatServiceTest`(스트리밍·폴백 분류·핵심서비스 무영향·N턴 윈도잉), `controller/BuyerChatControllerTest`(권한/스코핑)
