# 구매자 챗봇 동시 요청 시 응답 실패 버그 수정

대상: ai/buyer-chatbot          <!-- 완료 시 docs/dev/ai/buyer-chatbot/changes/로 이동 (기존 001-buyer-chatbot.md 다음 번호) -->
담당: 전용운

이 기능은 이미 완료(커밋)된 상태이고(`docs/dev/ai/buyer-chatbot/changes/001-buyer-chatbot.md`), 이번 문서는 그 기능에서 발견된 **버그 수정** 계획이다. 관련 기존 설계: `docs/dev/ai/buyer-chatbot/design.md`. 대상 코드: `BuyerChatService.streamChat()`(`src/main/java/com/gong9ri/gong9ri/service/BuyerChatService.java` 145~181행) 및 그 주변(`GlobalExceptionHandler`, `ChatLogRecorder`).

## 배경 / 요구 — 실측 근거

로컬 bootRun + 테스트 구매자 계정으로 직접 재현·확인했다.

- **순차 질문 2번(Tool Calling 포함)은 완전히 정상**: 각 4~5초, `/api/chat/stats` p95 4982ms, 에러 0%, JVM 힙도 안정적(112MB → 141MB).
- **같은 계정으로 동시에 10개 질문을 한꺼번에 보내면**: 1개만 5초 만에 정상 완료되고, **나머지 9개는 30초, 60초가 지나도 응답을 못 받는다.**
- 서버 로그 실측(타임스탬프 포함, 발췌):
  ```
  WARN DefaultHandlerExceptionResolver : Resolved [org.springframework.web.context.request.async.AsyncRequestTimeoutException]
  WARN ExceptionHandlerExceptionResolver : Failure in @ExceptionHandler ... handleException(Exception)
  org.springframework.http.converter.HttpMessageNotWritableException: No converter for [class com.gong9ri.gong9ri.common.response.ApiResponse] with preset Content-Type 'text/event-stream'
  WARN BuyerChatService : SSE 전송 실패, 연결이 이미 끊어졌을 수 있음: ResponseBodyEmitter has already completed
  ```
- `/api/chat/stats`로 확인한 결과, 10개 요청 중 `chat_interaction_log`에는 **3개만 기록**됐다. 기존 설계 목표(`design.md` "장애격리"/"트랜잭션" 섹션)는 "성공/실패 관계없이 모든 시도를 1행씩 기록"인데, 이 실패 경로에서는 그 기록 자체가 통째로 빠진다.
- 실패 후 20초, 그리고 몇 분 더 기다려도 나머지 요청들은 끝까지 `chat_interaction_log`에 안 잡혔다 — 뒤에서 계속 돌고 있는지 조용히 죽었는지는 로그만으로 100% 확증하지 못했다.

## 코드로 확인한 것 (원인의 일부 — 전부는 아님)

1. `BuyerChatService.java:55`의 `LLM_TIMEOUT = Duration.ofSeconds(15)`(Reactor `.timeout()`, `stream().chatResponse()` 체인에 적용)가 이 동시 요청 상황에서는 발동하지 않고, 대신 `EMITTER_TIMEOUT_MS = 60_000L`(`BuyerChatService.java:57`, `SseEmitter` 자체의 타임아웃)이 먼저 발동해 연결이 강제로 끊긴다. 왜 15초짜리가 안 걸렸는지는 Spring AI Tool Calling 내부 동작(스트림 구독 전 도구 호출 라운드트립이 블로킹으로 처리되는지 등)까지 확증하지 못했다 — **추정**으로만 남긴다.
2. `streamChat()`(145~181행)에는 `emitter.onTimeout(...)`/`emitter.onCompletion(...)` 핸들러가 없다. 즉 `SseEmitter`가 타임아웃/완료돼도 그 뒤에서 계속 돌고 있을 Reactor 구독(OpenAI로의 스트림 구독)을 취소하는 코드가 없다. SSE 연결이 끊긴 뒤에도 구독이 계속 살아있는지는 확증하지 못했지만, 이 상태가 반복되면 리소스가 계속 쌓일 수 있다는 게 우려 지점이다.
3. 그 타임아웃 상황에서 Spring 기본 예외 처리(`GlobalExceptionHandler`의 `@ExceptionHandler(Exception.class)`, `handleException`)가 이미 `text/event-stream`으로 커밋된 응답에 JSON(`ApiResponse`)을 쓰려다 실패해서 `HttpMessageNotWritableException`이 추가로 발생한다.
4. 타임아웃으로 끝난 턴은 `ChatLogRecorder.recordFailureTurn()`이 호출되지 않는다 — `streamChat()`의 Reactor 구독은 `onError`/`onComplete` 콜백(`onError()`/`onComplete()` 메서드, 174~178행)에서만 로그를 남기는데, `SseEmitter` 자체의 60초 타임아웃은 이 구독의 `onError`/`onComplete`와 별개 경로라서 그쪽 로깅을 안 타는 것으로 보인다(3번 항목의 예외 로그가 이 경로에서 나옴).

## 확증하지 못한 부분 — Generate 단계에서 먼저 진단 필요

"동시에 10개를 보내면 9개가 실패한다"는 사실과 "60초 emitter 타임아웃이 15초 LLM 타임아웃보다 먼저 발동한다"는 사실은 로그로 확인했지만, **정확히 어느 지점에서 병목이 생기는지는 코드만 읽어서는 단정할 수 없다**. 참고할 수 있는 전제만 나열한다(해결책 아님):

- Tomcat 스레드 풀은 `application.yaml`에서 최대 50개로 낮춰져 있다(2026-08-13, 프로덕션 메모리 제한 대응).
- HikariCP 커넥션 풀은 별도 설정이 없어 기본값을 쓴다.
- `ChatbotTools`는 요청마다 `new`로 생성되는 것으로 설계돼 있어(동시 요청 간 `buyerId` 격리 목적, `design.md` 참고) 이 자체가 동시성 버그의 직접 원인일 가능성은 낮아 보이지만, 진단 단계에서 재확인이 필요하다.
- 이번 재현은 매번 `sessionId` 없이 보낸 것으로 보여(10개 모두 새 세션 생성 경로) `ChatSession`의 `lastMessageAt`/`touch()` 갱신 같은 **같은 세션 동시 접근 데이터 정합성 문제**는 이번 재현의 직접 원인은 아닌 것으로 보이나, 존재 자체는 별도 리스크로 남긴다(아래 리스크 참고).

## 설계 방향 (접근 — 구체 구현은 Generate가 정한다)

1. **진단 우선**: 위 "확증하지 못한 부분"을 코드 수정 전에 먼저 진단한다(스레드 덤프, 서버 로그의 스레드명/타이밍 등으로 병목 지점 확인). 정확한 병목을 모른 채 타임아웃 값만 바꾸면 재발할 수 있다.
2. **구독 정리**: `SseEmitter`가 타임아웃되거나 완료될 때, 그 뒤에서 돌고 있는 Reactor 구독(OpenAI 스트림)을 확실히 취소할 수 있는 구조로 만든다 — 현재는 연결이 끊긴 뒤에도 구독을 정리하는 코드가 아예 없다는 사실 자체가 문제이므로, 이 생명주기를 명시적으로 연결한다.
3. **실패 로깅 보장**: `SseEmitter`가 타임아웃/비정상 종료되는 경로에서도 `chatLogRecorder.recordFailureTurn()`이 반드시 호출되게 한다(기존 설계 목표 "성공/실패 관계없이 매 시도 1행" 회복). 단, 기존 `onError()` 경로와 중복 기록되지 않게 한다(하나의 턴은 성공/실패 로그가 정확히 1행만 남아야 한다는 기존 설계 원칙 유지).
4. **SSE 컨텍스트에서 JSON 예외 응답 회피**: `GlobalExceptionHandler`의 catch-all이 이미 `text/event-stream`으로 커밋된 응답에 JSON을 쓰려다 `HttpMessageNotWritableException`을 유발하는 상황을 피한다. 다른 일반 API(JSON 응답) 엔드포인트의 기존 에러 응답 동작에는 회귀가 없어야 한다.
5. **두 타임아웃의 관계 재정렬**: `LLM_TIMEOUT`(15초)과 `EMITTER_TIMEOUT_MS`(60초)는 둘 다 design.md에 "실측 근거 없는 초기값"이라고 이미 명시돼 있다. 이번 진단 결과에 따라, 의도(LLM_TIMEOUT이 먼저 발동해서 폴백 안내를 사용자에게 정상적으로 보여줘야 함)대로 두 값이 실제로 상호작용하도록 관계를 재점검한다. 정확한 초 단위 값 자체는 이번 계획에서 확정하지 않는다(진단 결과 + Generate 판단).

## 태스크

- [ ] 동시성 실패 메커니즘 진단(스레드 덤프/로그 타이밍 등으로, Tomcat 스레드 풀/HikariCP 커넥션 풀/Reactor 스케줄러/Tool Calling 내부 블로킹 중 어디가 병목인지 확인)
- [ ] 진단 결과를 바탕으로 `SseEmitter` 타임아웃/완료 시 Reactor 구독을 정리하는 구조 적용
- [ ] 타임아웃/비정상 종료 경로에서도 `recordFailureTurn()`이 정확히 1회 호출되도록 보정(기존 `onError`/`onComplete` 경로와 안 겹치게)
- [ ] SSE 컨텍스트에서 `GlobalExceptionHandler`의 JSON 예외 응답이 이미 커밋된 SSE 응답에 쓰이지 않도록 처리(다른 JSON API 엔드포인트 회귀 없음 확인)
- [ ] `LLM_TIMEOUT`/`EMITTER_TIMEOUT_MS` 관계 재점검(진단 결과 반영, 필요 시 값 조정)
- [ ] 테스트 보강: `BuyerChatServiceTest`(에미터 타임아웃/비정상 종료 시 `recordFailureTurn` 호출 검증, 지연 응답 시 구독 정리 검증), 필요 시 `GlobalExceptionHandler` 관련 테스트, 동시 요청 재현 확인(수동 부하 확인 포함 가능 — 유닛 테스트로 실제 네트워크 동시성을 완전히 재현하기 어려울 수 있음을 감안)
- [ ] `docs/dev/ai/buyer-chatbot/design.md` 갱신(장애격리/트랜잭션 섹션에 이번 수정 내용 반영) + `docs/dev/ongoing/buyer-chatbot-concurrent-timeout-bug.md`를 `docs/dev/ai/buyer-chatbot/changes/002-*.md`로 채번 이동(현재 최대 번호가 001이므로 002)
- [ ] `docs/logs/ai/buyer-chatbot/`에 이번 시도 로그 추가(기존 `001-buyer-chatbot.md`에 이어 append, 또는 별도 파일 — Evaluate 단계에서 로그 가이드에 따름)

## 평가(통과) 기준

- `./gradlew test` 전체 통과(기존 `BuyerChatServiceTest`, `ChatLogRecorderTest` 포함, 회귀 없음).
- 동시에 N개(예: 10개, 재현 때와 동일 조건)의 챗봇 요청을 보냈을 때, **각 요청이 60초 emitter 타임아웃까지 방치되지 않고** 합리적 시간 안에 성공 응답 또는 명확한 실패(폴백 `message` 이벤트 + `done` 이벤트)로 종료되어야 한다.
- 위 동시 요청 N개에 대해 **`chat_interaction_log`에 성공/실패 관계없이 N행이 전부 기록**되어야 한다(재현 시의 "10개 중 3개만 기록됨" 문제가 재발하지 않음).
- 재현 시 나타난 `HttpMessageNotWritableException`이 서버 로그에 재발하지 않아야 한다.
- 기존 SSE 정상 동작(세션/메시지/폴백 이벤트 3종, Tool Calling, RAG 결합, N턴 윈도잉)에 회귀가 없어야 한다.
- 다른 JSON API 엔드포인트(예: `BusinessException`, 유효성 검증 실패 등)의 기존 에러 응답 형식에 회귀가 없어야 한다.

## 리스크 / 전제

- 15초 `LLM_TIMEOUT`이 동시 요청 상황에서 발동하지 않는 정확한 메커니즘은 Spring AI Tool Calling 내부 동작에 달려 있어 100% 확증하지 못했다(추정) — 진단이 선행되어야 정확한 수정 지점을 알 수 있다.
- `SseEmitter` 타임아웃/완료 시 배후 Reactor 구독이 실제로 계속 살아있는지(리소스 누적 여부)는 확증하지 못했다 — 반복 발생 시 서버 리소스에 영향을 줄 수 있다는 우려로만 남긴다.
- Tomcat 스레드 풀(최대 50, 최근 메모리 제한 대응으로 낮춘 값)과 HikariCP 기본 커넥션 풀이 동시 챗봇 요청과 경합할 가능성이 있으나, 정확한 병목 지점은 진단 전이므로 단정하지 않는다.
- 같은 `sessionId`로 동시에 여러 요청이 들어오는 경우(`ChatSession.lastMessageAt`/`touch()` 동시 갱신)의 데이터 정합성 문제는 이번 재현(매번 새 세션 생성 경로)의 직접 원인은 아닌 것으로 보이나, 별도로 존재할 수 있는 리스크로 남긴다 — 이번 계획의 핵심 범위는 아니다.
- `GlobalExceptionHandler`는 전역 공용 컴포넌트라, SSE 전용 처리를 분리할 때 다른 엔드포인트의 기존 JSON 에러 응답에 회귀가 없는지 확인이 필요하다.
- 기존 테스트는 Mockito 목 `ChatClient`를 사용해 실제 Reactor 스케줄러/네트워크 지연을 재현하지 않으므로, 이번 동시성 버그를 유닛 테스트만으로 완전히 재현하기 어려울 수 있다 — 평가 시 수동 동시 요청 확인(재현 때와 같은 방식)을 병행해야 한다.

## 문서 산출물

- 이 계획 문서: `docs/dev/ongoing/buyer-chatbot-concurrent-timeout-bug.md`
- Evaluate 통과 시 `docs/dev/ai/buyer-chatbot/design.md` 갱신 후, 이 문서를 `docs/dev/ai/buyer-chatbot/changes/002-*.md`로 채번 이동.
- 관련 기존 문서(참조만, 이번에 직접 수정하지 않음): `docs/dev/ai/buyer-chatbot/changes/001-buyer-chatbot.md`, `docs/logs/ai/buyer-chatbot/001-buyer-chatbot.md`, `docs/api/chat.md`.
