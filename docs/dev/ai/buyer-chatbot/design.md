# 구매자 챗봇 (ai/buyer-chatbot) — Design

## 개요

구매자가 자연어로 채팅하면 AI가 필요시 백엔드 API(Tool)를 직접 호출해서 실시간 데이터를 근거로 답하고, 정책 관련 질문에는 RAG로 검색한 정책 문서 스니펫을 근거로 답한다. 응답은 SSE로 토큰 단위 스트리밍되고, 대화는 세션 단위로 저장되어 멀티턴이 가능하다. 발제 AI 필수2(Tool Calling), 필수3(장애격리·비용인식), 도전(SSE 스트리밍+멀티턴)을 이 기능 하나로 묶어 구현했고, AI 도전 나머지 하나(RAG, 전용운이 `ai/policy-rag`로 검색 부분만 별도 구현)는 이 서비스에 실제로 결합했다(아래 "RAG 결합" 참고).

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
- **Tool 호출 실패 시 자연어 안내**: Spring AI의 `DefaultToolExecutionExceptionProcessor`가 기본적으로 `@Tool` 메서드의 예외를 그대로 죽이지 않고 메시지를 LLM에 tool 응답으로 돌려줘서 자연어로 안내하게 하는 구조(jar 디컴파일로 `alwaysThrow` 옵션 존재 확인). **실제 재현 검증 완료**(재점검 중 "디컴파일 근거만 있고 실제 테스트가 없다"는 걸 스스로 발견해서 보강) — `searchProducts`에 특정 키워드로만 발동하는 임시 예외 트리거를 잠깐 추가해 실제 OpenAI 호출로 확인 후 제거함. 예외가 나도 SSE 스트림은 `onError`(내 장애격리 폴백)가 아니라 정상 `onComplete` 경로로 흘러서, LLM이 "현재 상품 검색 서비스에 일시적인 장애가 발생하여 검색할 수 없습니다..."로 자연어 응답, `chat_interaction_log`에도 `success=true`로 기록됨(도구 실패지만 대화 턴 자체는 정상 완료로 처리) — 상세: `docs/logs/ai/buyer-chatbot/001-buyer-chatbot.md` Attempt 3.

## 장애격리 (발제 AI 필수3)

- **Fallback 1 — 타임아웃**: `.stream().chatResponse()`(`Flux<ChatResponse>`)에 `.timeout(Duration.ofMillis(llmTimeoutMs))`. `gong9ri.chat.llm-timeout-ms`(기본 15000) 설정값 — 원래 상수(15초, 실측 근거 없는 초기값)였다가 동시성 버그 수정(`changes/002-*.md`) 중 테스트에서 타임아웃 경로를 실제 시간 경과로 검증할 수 있도록 설정값으로 뺐다. 값 자체(15초)는 바뀌지 않았다.
- **Fallback 2 — API 장애**: `com.openai.errors.RateLimitException`/`InternalServerException`/그 외 예외를 잡아 별도 안내. 에러 유형은 `ChatErrorType`(TIMEOUT/RATE_LIMIT/SERVER_ERROR/OTHER) 4종으로 로그에 구분 기록(대시보드 breakdown용)하되, 사용자에게 보여주는 문구는 "타임아웃"과 "그 외 API 장애" 2가지로 충분하다고 판단(발제 "최소 2가지" 요구 충족).
- **Fallback 3 — SseEmitter 자체 타임아웃(연결 안전장치)**: `gong9ri.chat.emitter-timeout-ms`(기본 60000)로 `SseEmitter`를 생성하고 `emitter.onTimeout(...)`을 등록한다. 동시 요청 부하로 위 `llmTimeoutMs`가 제때 발동하지 못해도(아래 "동시 요청 처리" 참고), 이 콜백이 확실히 실패 턴 1건을 기록하고 폴백 이벤트(`message`+`done`)를 보낸 뒤 `emitter.complete()`를 직접 호출해서 컨테이너의 기본 `AsyncRequestTimeoutException` 경로(그리고 그로 인한 `HttpMessageNotWritableException`)를 타지 않게 한다.
- **핵심 서비스 무영향**: 챗봇 Tool은 전부 읽기 전용(상품 검색, 내 참여 조회) — 주문/결제 상태를 바꾸지 않음. `BuyerChatServiceTest.streamChat_failureDoesNotAffectCoreService`로 챗봇 실패 후에도 상품 저장(핵심 서비스 예시)이 정상 동작함을 직접 검증.
- **환각 방어**: 시스템 프롬프트에 "모르는 정보는 반드시 Tool을 호출해서 확인하고, 확인 안 된 사실을 지어내지 마라. 확실하지 않으면 모른다고 답하라" + "GONG9RI 공동구매 서비스와 무관한 질문에는 답하지 말고 안내만 하라"를 명시. 실제 호출로 (1) 서비스 무관 질문(날씨) 거절, (2) Tool로 확인 안 되는 정보(판매자 전화번호) "제공할 수 없다"고 답하는 것 둘 다 확인함(`docs/logs/ai/buyer-chatbot/001-buyer-chatbot.md`).

## SSE 스트리밍 + 멀티턴 (발제 도전)

- **스트리밍**: `ChatClient...stream().chatResponse()`(`Flux<ChatResponse>`)를 구독해서 `SseEmitter`로 텍스트 청크 전달. `SseEmitter`는 Spring MVC 표준 기능이라 webflux 없이도 동작함(실제 확인, `reactor-core`는 Spring AI가 이미 전이 의존성으로 가짐). SSE 이벤트는 `session`(세션ID, 스트림 시작 시 1회) / `message`(텍스트 청크, 반복) / `done`(스트림 종료, 1회) 3종 — 실패 시 폴백 안내문도 `message` 이벤트로 동일하게 보내서 클라이언트가 성공/실패를 특별취급 안 해도 되게 함.
- **세션 재사용/만료**: `ChatLogRecorder.getOrCreateSession` — `sessionId`가 없으면 새로 만들고, 있으면 소유자 확인(`FORBIDDEN`) 후 `lastMessageAt` 기준 30분 경과 여부로 재사용/신규 생성을 결정.
- **최근 N턴 윈도우(N=10, 최근 5턴)**: `ChatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc` → 시간순으로 뒤집어 `.messages(...)`에 포함. 근거: 상품 검색·내 참여 조회는 Tool Calling으로 매번 실시간 재조회하므로 오래된 대화 맥락 의존도가 낮음. 이 초기값도 실측 후 조정 여지 있음.
- **사용자 간 격리**: `ChatSession.buyer` FK로 스코핑, mypage와 동일한 본인 소유 확인 패턴.
- **토큰 사용량 추출**: 스트리밍 응답은 청크마다 `ChatResponseMetadata.getUsage()`가 항상 채워지지 않고 실측 결과 마지막 청크에만 채워짐(OpenAI 스트리밍 특성) — 스트림 도중 `Usage`가 non-null로 오는 마지막 값을 계속 갱신해두었다가 완료 시점에 사용.

## 동시 요청 처리 (동시성 버그 수정, `changes/002-*.md`)

동시에 여러 SSE 요청이 들어오면 대부분 실패하는 버그가 있었다. 로컬 재현(동시 10개 요청) + `jstack` 스레드
덤프 + 서버 로그 타임스탬프로 실측 진단한 근본 원인과 수정 내용은 다음과 같다.

- **근본 원인 — `spring.jpa.open-in-view`(OSIV) 기본값(`true`)으로 인한 HikariCP 커넥션 풀 고갈**: OSIV가
  켜져 있으면 비동기(SSE) 요청은 `SseEmitter`가 완료/타임아웃될 때까지 JDBC 커넥션 1개를 계속 붙들고
  있는다. 동시 10개 요청이 각자 커넥션 1개씩(HikariCP 기본 풀 크기 10개와 정확히 일치) 최대 60초간 쥐고
  있으면서, `ChatLogRecorder`의 `REQUIRES_NEW` 트랜잭션이 새 커넥션을 못 얻어 조용히 실패하고(실패 턴 기록
  누락), 챗봇과 무관한 `TeamDeadlineScheduler`까지 `CannotCreateTransactionException`으로 실패했다. 수정:
  `spring.jpa.open-in-view: false`(운영·테스트 설정 모두). 이 코드베이스는 REST 전용(뷰 렌더링 없음)이고
  컨트롤러가 엔티티를 직접 노출하지 않으며 DTO 매핑이 전부 `@Transactional` 서비스 메서드 안에서 끝나므로
  (`docs/code-convention.md`) OSIV가 애초에 필요 없다.
- **구독 생명주기 연결**: `streamChat()`의 `.subscribe(...)`가 반환하는 `Disposable`을 캡처해
  `emitter.onCompletion()`에서 정리한다. 이전에는 `SseEmitter`가 타임아웃/완료돼도 배후 Reactor 구독(OpenAI
  스트림)을 취소하는 코드가 없었다.
- **정확히 1행 기록 보장**: `AtomicBoolean turnFinished`로, Reactor `onError`/`onComplete`와 `SseEmitter`의
  `onTimeout`이 경쟁해도 정확히 1번만 `recordSuccessTurn`/`recordFailureTurn`이 호출되도록 가드한다(기존
  "성공/실패 관계없이 정확히 1행" 설계 원칙 유지). 두 기록 호출은 각각 `try/catch`로 감싸 그 자체가
  실패해도(DB 과부하 등) 최소한 ERROR 로그는 남도록 보강했다(관측성, 최후 방어선).
- **SSE 컨텍스트에서 JSON 예외 응답 회피**: `GlobalExceptionHandler.handleException`(catch-all)이
  `HttpServletResponse.isCommitted()`를 확인해, 이미 커밋된 응답(SSE 스트림 도중 타임아웃 등)이면 JSON
  바디를 쓰지 않고 `null`을 반환한다. 일반 JSON API는 예외 시점에 응답이 아직 커밋되지 않으므로 기존 동작과
  동일(회귀 없음).
- **결과**: 실제 OpenAI 호출로 동시 10개 요청 2라운드(총 20회) 재검증, 10/10 전부 정상 완료·
  `chat_interaction_log`/`chat_message` 정상 저장 확인(수정 전 3/10 → 수정 후 10/10). 상세 진단·검증 로그:
  `docs/logs/ai/buyer-chatbot/002-buyer-chatbot-concurrent-timeout-bug.md`.

## 트랜잭션 — DB 쓰기는 전부 `ChatLogRecorder`의 REQUIRES_NEW

SSE 스트리밍은 컨트롤러 요청 스레드가 즉시 `SseEmitter`를 반환한 뒤, 실제 완료/실패는 Reactor 스케줄러 스레드의 콜백에서 비동기로 처리된다 — 그 시점엔 기댈 "앰비언트 트랜잭션" 자체가 없다. 그래서 세션 생성/조회, 메시지 저장, 로그 저장을 전부 `ChatLogRecorder`(별도 빈)의 `@Transactional(REQUIRES_NEW)` 메서드로 통일했다. 이는 `AiSuggestionLogRecorder`(AI 필수1)와 전용운의 `NotificationService`에서 이미 두 번 겪은 "실패를 잡아 로그 저장 후 예외를 다시 던지면 롤백에 로그까지 같이 사라지는" 문제의 세 번째 적용 사례이기도 하다.

- **성공 시**: `ChatMessage`(USER) + `ChatMessage`(ASSISTANT) + `ChatInteractionLog.success` 저장 + 세션 `touch()`.
- **실패 시**: `ChatMessage`(USER)만 저장(어시스턴트 메시지는 실제 응답이 아니므로 저장 안 함) + `ChatInteractionLog.failure` 저장 + 세션 `touch()`.

## RAG 결합 (발제 도전 "RAG+Tool Calling 결합")

`ai/policy-rag`(전용운)가 만든 `PolicyRagService.findRelevantSnippets(query)`(REST 아닌 인터페이스, 생성자 주입)를 `streamChat()`에서 매 턴 호출한다.

- **호출 시점**: 세션 조회/이력 로딩과 마찬가지로 스트림 시작 전, 원래 요청 스레드에서 동기로 호출(임베딩 API 1회 호출이라 짧게 걸림). 검색 결과를 시스템 프롬프트에 조립한 뒤에 `ChatClient` 스트림을 시작한다.
- **장애 격리**: `PolicyRagService` 호출이 실패해도(임베딩 API 장애 등) `try/catch`로 잡아 빈 목록으로 처리하고 챗봇 턴 자체는 정상 진행한다 — RAG는 답변을 보강하는 부가 기능이지 챗봇 동작의 필수 전제가 아님(`BuyerChatServiceTest.streamChat_ragServiceThrows_stillProceedsNormally`로 검증).
- **관련성 판단은 시스템 프롬프트가 맡는다**: `PolicyRagService`는 항상 topK를 그대로 반환하고 관련 없는 스니펫을 걸러주지 않는다(`ai/policy-rag` 설계, threshold 필터링이 이 코퍼스 규모에서 신뢰할 수 없다고 실측 확인됨). 그래서 시스템 프롬프트 뒤에 스니펫을 붙일 때 "질문과 관련이 없으면 참고하지 말고 무시해라"는 지시를 같이 넣는다.
- **실제 통합 중 발견한 프롬프트 함정**: 첫 실호출 검증에서, RAG 스니펫이 정확한 답을 담고 있는데도 모델이 "정확한 정보는 확인할 수 없습니다"로 불필요하게 얼버무리는 걸 발견했다. 원인은 기존 시스템 프롬프트의 "도구로 확인하지 않은 사실을 추측해서 답하지 마라"는 지시가 RAG로 확인된 정책 정보까지 "확인 안 된 것"으로 취급하게 만든 것 — 프롬프트에 "아래 제공되는 정책 문서로도 확인 안 된 사실을 추측하지 마라(반대로) 관련된 정책 스니펫이 제공되면 그건 이미 확인된 사실이니 망설이지 말고 답변 근거로 사용해라"를 명시해서 해결. 실제 호출로 재검증: "제 돈은 언제 돌려받을 수 있나요?"(정책 문서와 어휘가 겹치지 않는 패러프레이즈, `ai/policy-rag` Attempt 2에서 어려움을 겪었던 표현) → "공구 기한이 지나면 자동으로 실패 처리 및 환불이 진행됩니다"로 정확하고 확신 있게 답변. 서비스 무관 질문(날씨) 거절은 회귀 없이 그대로 유지됨.
- **출처표시(발제 RAG 요구사항)**: 재점검 중 시스템 프롬프트에 "답변에 RAG 스니펫을 썼으면 출처를 밝혀라"는 지시 자체가 아예 없었다는 걸 발견 — 처음엔 각 스니펫 맨 앞의 "# 문서 제목"을 그대로 인용하게 했다. 실제 호출로 검증: "환불은 언제 되나요?" → 답변 끝에 "(출처: 공구팀 실패(미성사) 및 환불 트리거)"가 붙어서 나옴 — 청킹 시점에 각 청크 앞에 문서 제목을 붙여두는 `PolicyDocumentIndexer`의 기존 설계(전용운) 덕분에 별도 메타데이터 전달 없이 프롬프트 지시만으로 해결됨.
- **출처 이름을 내부 문서 제목과 분리(후속 보강)**: "미성사", "트리거" 같은 표현이 담긴 내부 문서 제목을 구매자에게 그대로 노출하는 게 어색하다는 지적을 받아, `PolicyDocumentIndexer`가 청크에 고객 응대용 "표시용 출처명"(예: "환불 정책")을 내부 제목과 별도 줄로 함께 임베딩하도록 바꾸고(전용운), 이 시스템 프롬프트의 출처표시 지시도 "표시용 출처명" 줄만 인용하고 내부 제목(`#`으로 시작하는 줄)은 절대 쓰지 말라는 내용으로 갱신했다. 실제 호출로 재검증: "환불은 언제 되나요?" → 답변 끝에 정확히 "(출처: 환불 정책)"만 붙고 내부 제목("공구팀 실패")은 인용에 등장하지 않음을 확인.

## 비용인식 — 대시보드

`GET /api/chat/stats`(모델별 누적 토큰·P95 응답지연·에러율), `GET /api/buyer/chat/sessions/{id}/usage`(세션별 누적 토큰). 이 프로젝트 데이터 규모에서는 DB 퍼센타일 함수 대신, 로그를 모델별/세션별로 모아 애플리케이션 레벨에서 정렬 후 계산하는 방식을 택함(단순함 우선). 별도 프론트 대시보드 UI는 스코프 밖 — 전용운이 필요하면 이 JSON을 그대로 붙여 쓸 수 있음.

## 관련 코드 위치

- `entity/{ChatSession,ChatMessage,ChatRole,ChatInteractionLog,ChatErrorType}.java`
- `repository/{ChatSessionRepository,ChatMessageRepository,ChatInteractionLogRepository}.java`, `ProductRepository.findTop10ByNameContainingIgnoreCase`(신규)
- `dto/{ChatMessageRequest,ChatMessageResponse,ChatSessionUsageResponse,ChatStatsResponse,ProductSearchResult}.java` — `BuyerTeamResponse`(기존 mypage DTO)를 Tool 2 응답에 재사용
- `service/{BuyerChatService,ChatLogRecorder,ChatbotTools}.java` — `ChatbotTools`는 요청마다 `new`
- `service/{PolicyRagService,PolicyRagServiceImpl}.java`, `config/{PolicyRagVectorStoreConfig,PolicyDocumentIndexer}.java` — 전용운 담당(`ai/policy-rag`), `BuyerChatService`가 생성자 주입으로 사용
- `controller/BuyerChatController.java`
- `common/exception/ErrorCode.java` — `CHAT_SESSION_NOT_FOUND` 추가
- `common/exception/GlobalExceptionHandler.java` — `handleException`의 `isCommitted()` 체크(SSE 응답 커밋 후 예외 시 JSON 바디 생략, 위 "동시 요청 처리" 참고)
- `application.yaml`(`spring.jpa.open-in-view: false`, `gong9ri.chat.llm-timeout-ms`/`emitter-timeout-ms`), `src/test/resources/application.yaml`(테스트 전용 짧은 타임아웃 값)
- 테스트: `service/ChatbotToolsTest`(Tool 메서드 직접 단위 테스트 — mock ChatClient로는 Spring AI의 실제 tool 실행 메커니즘을 안 타므로), `service/ChatLogRecorderTest`(세션 생성/재사용/만료/소유권, 성공·실패 턴 기록), `service/BuyerChatServiceTest`(스트리밍·폴백 분류·핵심서비스 무영향·N턴 윈도잉·RAG 결합), `controller/BuyerChatControllerTest`(권한/스코핑)
