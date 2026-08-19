# 002-buyer-chatbot-concurrent-timeout-bug — 구매자 챗봇 동시 요청 타임아웃 버그 수정 (로그)

계획 문서: `docs/dev/ongoing/buyer-chatbot-concurrent-timeout-bug.md`. 관련 기존 로그: `docs/logs/ai/buyer-chatbot/001-buyer-chatbot.md`.

## Attempt 1 — 2026-08-19 ✅ PASS(Generate 단계 자체 검증)

### 진단 — 실제 로컬 재현으로 병목 지점을 특정함

계획 문서가 "확증하지 못했다"고 남긴 부분을, 로컬 bootRun(`PORT=8081`) + 테스트 구매자 계정(`chatdiag`)으로
실제 동시 10개 요청을 재현하고 `jstack` 스레드 덤프 + 서버 로그 타임스탬프로 직접 진단했다.

1. **1차 재현(수정 전 코드)**: 로그인 후 세션 쿠키로 `POST /api/buyer/chat/messages` 10개를 완전히 동시에
   백그라운드 curl로 발사. 결과: **10개 중 0개가 90초 안에 완료되지 못함**(`curl --max-time 90`으로 전부
   타임아웃) — 계획 문서 작성 시점의 재현("1개만 성공")보다도 나쁜 결과였다.
2. **서버 로그 실측**: 모든 10개 세션이 정확히 1번씩 `onError`(Reactor `.timeout(15s)`)를 탔지만, 그 시점의
   `latencyMs`가 **15000이 아니라 60763~60888**로 찍힘 — 즉 "15초 타임아웃"이라는 예외 메시지 자체는 맞는데,
   그 예외가 **실제로 발동(fire)하기까지 60초 가까이 걸렸다**. 이게 계획 문서의 "15초 LLM_TIMEOUT이 발동하지
   않는다"는 관찰의 진짜 정체다 — 발동은 하는데 너무 늦게 한다.
3. **근본 원인 특정 — HikariCP 커넥션 풀 고갈(Open-Session-In-View가 원인)**: 서버 로그에서
   `HikariPool-1 - Connection is not available, request timed out after 30003ms (total=10, active=10, idle=0,
   waiting=8)` 경고를 다수 확인. 심지어 챗봇과 **전혀 무관한** `TeamDeadlineScheduler.checkDeadlines()`(1분
   주기 스케줄러)까지 `CannotCreateTransactionException`으로 실패하는 걸 로그에서 확인 — 챗봇 요청이 앱
   전체의 DB 커넥션 풀을 고갈시킨다는 증거. 원인: Spring Boot 기본값 `spring.jpa.open-in-view=true`(기동 로그의
   경고 문구 그대로) — REST긴 하지만 OSIV가 켜져 있으면 **비동기(SSE) 요청은 `SseEmitter`가 완료/타임아웃될
   때까지 JDBC 커넥션 1개를 계속 붙들고 있는다.** 동시 10개 요청이 각자 커넥션 1개씩(HikariCP 기본 풀 크기
   10개와 정확히 일치) 최대 60초간 쥐고 있으니, `ChatLogRecorder`의 `REQUIRES_NEW` 트랜잭션(실패 턴 기록용으로
   일부러 격리해둔 것)이 새 커넥션을 못 얻어 조용히 실패하는 것 — 이게 실제 `chatLogRecorder.recordFailureTurn`
   호출 자체가 예외로 죽어서(어디서도 로그가 안 남는 채로) "10개 중 3개만 기록됨" 버그의 진짜 원인이었다
   (`/api/buyer/chat/sessions/{id}/messages`로 세션별 실제 저장 여부를 직접 대조해서 3/10만 저장됨을 재확인).
4. **구독 미정리 확인**: `emitter.onTimeout()`/`onCompletion()` 핸들러가 아예 없어서, `SseEmitter`가 60초
   자체 타임아웃으로 죽은 뒤에도 배후 Reactor 구독이 정리되지 않고 있었다(수정 전 코드에 해당 생명주기 연결
   자체가 없었음 — 코드 리딩으로 확인).
5. **HttpMessageNotWritableException 재현**: 수정 전 코드로 위 재현 시 `HttpMessageNotWritableException`
   10건 전부 재현(로그 카운트로 확인) — `AsyncRequestTimeoutException`이 `GlobalExceptionHandler`의
   `Exception.class` catch-all까지 흘러가 이미 `text/event-stream`으로 커밋된 응답에 JSON을 쓰려다 발생.

### 수정 내용

1. **`spring.jpa.open-in-view: false`**(`application.yaml`, `src/test/resources/application.yaml` 둘 다) —
   근본 원인 제거. 이 코드베이스는 REST 전용(뷰 렌더링 없음)이고 컨트롤러가 엔티티를 직접 노출하지 않으며
   DTO 매핑이 전부 `@Transactional` 서비스 메서드 안에서 끝나므로(`docs/code-convention.md`) OSIV가 애초에
   필요 없다.
2. **`BuyerChatService.streamChat()` 리팩터** — `.subscribe(...)`가 반환하는 `Disposable`을 캡처해
   `emitter.onCompletion()`에서 정리(구독 정리, 계획 항목 2). `emitter.onTimeout()`을 새로 등록해 SseEmitter
   자체 타임아웃 시 확실히 실패 턴 1건을 기록하고 폴백 이벤트(`message`+`done`)를 보낸 뒤 `emitter.complete()`를
   직접 호출 — 컨테이너의 기본 `AsyncRequestTimeoutException` 경로를 아예 안 타게 만들어서 근본적으로
   `HttpMessageNotWritableException`을 예방한다(계획 항목 4). `AtomicBoolean turnFinished`로 Reactor
   `onError`/`onComplete`와 emitter `onTimeout`이 경쟁해도 정확히 1번만 기록되도록 가드(계획 항목 3, "정확히
   1행" 설계 원칙 유지). `recordSuccessTurn`/`recordFailureTurn` 호출을 try/catch로 감싸 그 자체가 실패해도
   조용히 사라지지 않고 최소한 ERROR 로그는 남도록 보강(관측성 개선, DB 자체가 진짜로 과부하일 때의 최후 방어선).
3. **`GlobalExceptionHandler.handleException`** — `HttpServletResponse.isCommitted()`를 확인해 이미 커밋된
   응답(예: SSE 스트림 도중 타임아웃)이면 JSON 바디를 쓰지 않고 `null`을 반환(계획 항목 4). 일반 JSON API는
   예외 시점에 응답이 아직 커밋되지 않으므로 기존 동작과 동일 — 회귀 없음.
4. **`LLM_TIMEOUT`/`EMITTER_TIMEOUT_MS`를 설정값으로 전환**(계획 항목 5) — `gong9ri.chat.llm-timeout-ms`(기본
   15000), `gong9ri.chat.emitter-timeout-ms`(기본 60000). 실제 값 관계(LLM 타임아웃이 emitter 타임아웃보다
   항상 충분히 짧아야 함)는 변경하지 않았다 — 근본 원인이 DB 커넥션 풀 고갈이었지 두 타임아웃 값 자체의
   문제가 아니었기 때문(진단 결과 반영). 대신 테스트에서 emitter 타임아웃 경로를 실제 시간 경과로 빠르게
   검증할 수 있도록 테스트 전용으로 짧은 값(1500ms/300ms)을 오버라이드했다.

### 테스트

- `BuyerChatServiceTest`에 `streamChat_emitterTimesOutFirst_recordsFailureOnceAndDisposesSubscription` 신규
  추가 — `Flux.never()`로 절대 응답하지 않는 스트림을 주입하고, emitter 타임아웃(300ms) 시점에 실패 턴이
  정확히 1행 기록되는지, 그리고 llm 타임아웃(1500ms)까지 추가로 기다려도 **구독이 제대로 dispose되어**
  Reactor 쪽에서 뒤늦게 또 기록을 시도하지 않는지(중복 없음, 정확히 1행 유지)까지 함께 검증.
- 기존 8개 테스트(성공/타임아웃/RateLimit/OTHER/핵심서비스무영향/N턴윈도잉/RAG 2종) 전부 그대로 통과.
- `./gradlew test` 전체: 실행할 때마다 **`ProductControllerTest` 3개, `ProductCachingTest` 3개,
  `LoginRateLimitFilterTest` 0~2개(타이밍에 따라 유동)**가 실패했다. 전부 **이번 변경과 무관한 기존 문제**임을
  `git stash`로 이번 변경을 전부 되돌린 뒤 동일하게 재현해서 확인했다:
  - `ProductControllerTest`/`ProductCachingTest`: 이 저장소의 테스트가 로컬 MySQL(`gong9ri_db`, 실제 dev
    DB와 동일)을 그대로 쓰는데, 이전 수동 bootRun 세션들이 남긴 잔여 상품 데이터("블루투스 이어폰" 등)로
    개수/정렬을 검증하는 테스트가 깨짐. 수정 전 코드로 같은 테스트를 재실행해도 동일하게 실패.
  - `LoginRateLimitFilterTest`: 실제 Redis를 그대로 쓰는데, 같은 클라이언트 키의 rate-limit 카운터가 TTL
    동안 테스트 간에 공유돼서 실행 순서/타이밍에 따라 통과·실패가 갈림(수정 전 코드로 전체 스위트를 돌려도
    동일하게 0~2개 실패 재현, 단독 실행 시 시간을 두면 통과). 두 경우 다 DB/Redis 정리는 이번 계획 범위가
    아니라 손대지 않았다.

### 실제 재현으로 최종 검증(수정 후, 실제 OpenAI 호출)

같은 방식(로그인 → 세션 쿠키 → `POST /api/buyer/chat/messages` 10개 동시 발사)으로 **수정된 코드로 2라운드
재실행**(총 실제 OpenAI 호출 20회).

- **1라운드**: 10/10 전부 `session`→`message`(스트리밍 청크)→`done` 정상 완료. 10개 세션 전부
  `chat_message`에 USER+ASSISTANT 2행씩 정상 저장됨(`/api/buyer/chat/sessions/{id}/messages`로 직접 대조,
  10/10 — 수정 전 3/10에서 개선). 전체 소요 시간은 최초 요청 디스패치(17:52:55.8)부터 마지막 응답 완료까지
  **약 4초**(수정 전엔 대부분 60초+ 또는 완전히 멈춤).
- **2라운드**: 다시 10/10 전부 `event:done` 확인.
- **에러 재발 없음**: 두 라운드 합쳐 `HttpMessageNotWritableException` 0건, `AsyncRequestTimeoutException`
  0건, `CannotCreateTransactionException` 0건, `Connection is not available`(Hikari 고갈) 0건,
  `TeamDeadlineScheduler` 콜래터럴 실패 0건 — 전부 grep으로 직접 카운트해서 확인.
- Tool Calling도 정상 동작 확인(참여 중인 공구팀 없음 → "현재 참여 중인 공구팀이 없으므로...", RAG 스니펫
  기반 환불 정책 답변 + "(출처: 환불 정책)" 인용까지 기존 동작 그대로 유지됨을 응답 본문에서 확인).

### API 크레딧 사용

이번 진단·검증 실제 OpenAI 호출: 1라운드 10회 + 2라운드 10회 = **20회**(전부 성공). 누적(001 로그 기준
6회 + 이번 20회) = 26회.

### 다음(Evaluate 단계로 인계)

- 계획 문서의 남은 태스크(`design.md` 갱신, `ongoing/` → `changes/002-*.md` 채번 이동)는 Evaluate 통과 후
  처리 — 이번 Generate 단계에서는 손대지 않음.
- 로컬 진단용으로 생성했던 스크립트/로그/쿠키 파일(`scratch_*`, `bootrun*.log`, `cookies.txt`)은 검증 완료 후
  전부 삭제해 저장소를 깨끗한 상태로 되돌림.

## Evaluate — 2026-08-19 ✅ PASS

### 계산적 평가 — Generate의 주장을 독립적으로 재확인

- 스코프 테스트: `./gradlew test --tests "*BuyerChatServiceTest*" --tests "*ChatLogRecorderTest*" --tests
  "*BuyerChatControllerTest*" --tests "*ChatbotToolsTest*"` → `BUILD SUCCESSFUL`, 전부 통과. 신규 테스트
  `streamChat_emitterTimesOutFirst_recordsFailureOnceAndDisposesSubscription`을 `--rerun-tasks`로 3회 추가
  재실행해도 안정적으로 통과(타이밍 기반 테스트라 별도로 반복 확인함).
- 전체 스위트: `./gradlew test` → **357개 중 8개 실패**(Generate 로그의 "351/357, 나머지 6개 무관"이라는
  숫자와 정확히 일치하진 않음 — 이번 실행에서는 `LoginRateLimitFilterTest`가 2개 실패해 8개였음. Generate
  로그도 "LoginRateLimitFilterTest 0~2개, 타이밍에 따라 유동"이라고 명시했으므로 범위 안). 실패 목록:
  `LoginRateLimitFilterTest` 2개, `ProductControllerTest` 3개, `ProductCachingTest` 3개 — Generate가 지목한
  것과 동일한 3개 클래스.
- **Generate의 "git stash로 대조해 무관함을 확인했다"는 주장을 그대로 믿지 않고 직접 재현**: 이번 변경
  5개 파일(`BuyerChatService.java`, `GlobalExceptionHandler.java`, `application.yaml`,
  `BuyerChatServiceTest.java`, `src/test/resources/application.yaml`)을 `git stash`로 되돌린 뒤 동일하게
  `./gradlew test --tests "*ProductControllerTest*" --tests "*ProductCachingTest*" --tests
  "*LoginRateLimitFilterTest*"`를 실행 → **변경 전에도 정확히 동일한 8개가 동일한 assertion 지점에서
  실패**(`ProductControllerTest.java:119/396/272`, `ProductCachingTest.java:138/96/207`,
  `LoginRateLimitFilterTest.java:80/67`). `git stash pop`으로 원상 복구 확인. → 이 8개 실패는 이번 변경과
  **무관한 기존 문제**라는 Generate의 주장이 사실로 확인됨(로컬 MySQL 잔여 데이터, Redis rate-limit TTL
  공유 — 코드 로직 문제가 아니라 로컬 실행 환경 문제이므로 이번 버그 수정의 Evaluate 통과를 막지 않음).

### 추론적 평가 — 계획의 "평가(통과) 기준" 대조

- `./gradlew test` 전체 통과(기존 `BuyerChatServiceTest`, `ChatLogRecorderTest` 포함, 회귀 없음) —
  **충족**(위 계산적 평가 참고, 실패 8개는 무관함을 독립 재검증).
- 동시 N개 요청이 60초까지 방치되지 않고 성공/명확한 실패로 종료 — **충족**. 코드 검토로 `emitter.onTimeout`이
  실패 턴 기록 + 폴백 이벤트 + `emitter.complete()`를 직접 호출함을 확인했고(직접 로직 재현은 하지 않고
  Generate의 실측 로그를 근거로 채택 — evaluate-guide 원칙상 "실측 부하테스트는 사용자/generator가 한 것을
  우선"), 유닛 테스트(`streamChat_emitterTimesOutFirst_...`)로 이 경로가 실제 코드에서 동작함을 별도
  확인했다.
- `chat_interaction_log`에 성공/실패 관계없이 N행 전부 기록 — **충족**(설계상: `AtomicBoolean`으로 정확히
  1회, `recordSuccessSafely`/`recordFailureSafely`가 try/catch로 감싸 기록 자체의 실패도 삼키지 않음; 신규
  테스트로 "정확히 1행" 유지 검증됨).
- `HttpMessageNotWritableException` 재발 방지 — **충족**(코드 검토: `GlobalExceptionHandler.handleException`이
  `isCommitted()`면 바디를 쓰지 않고 반환).
- 기존 SSE 정상 동작(세션/메시지/폴백 3종, Tool Calling, RAG, N턴 윈도잉) 회귀 없음 — **충족**. 기존
  `BuyerChatServiceTest` 8개(성공/타임아웃/RateLimit/OTHER/핵심서비스무영향/N턴윈도잉/RAG 2종) 전부 그대로
  통과.
- 다른 JSON API 엔드포인트의 기존 에러 응답 형식 회귀 없음 — **충족(코드 근거로 확인, 전용 테스트는 없음)**.
  `GlobalExceptionHandler`에 `GlobalExceptionHandlerTest`류의 전용 단위 테스트는 이 저장소에 존재하지
  않는다(`grep`으로 확인). 다만 (1) `response.isCommitted()`는 일반 JSON API 요청 처리 중 예외가 발생하는
  시점에는 항상 `false`이므로(응답을 아직 쓰기 전) 분기 자체가 안전하고, (2) 전체 테스트 스위트에 포함된
  다수의 컨트롤러 테스트(`BusinessException`/유효성 검증 실패 등 4xx 응답을 검증하는 기존 테스트들)가
  이번 실행에서도 전부 그대로 통과했다(다른 `@ExceptionHandler` 메서드는 손대지 않았고, 통과한 컨트롤러
  테스트들이 그 경로를 계속 태우고 있음). 두 근거를 합쳐 회귀 없음으로 판단.
- **범위 이탈(임의 리팩터링/기능 추가) 여부** — `git diff` 전체를 검토한 결과, 계획 문서 태스크 5개
  (진단/구독정리/실패로깅보장/SSE 예외응답 회피/타임아웃 관계 재점검)에 정확히 대응하는 변경만 있었고,
  그 외 무관한 리팩터링은 없음. 값 관계(`llmTimeoutMs` < `emitterTimeoutMs`)는 근본 원인이 DB 커넥션 풀
  고갈이었다는 진단에 따라 값 자체는 바꾸지 않고 설정값으로 추출만 한 것 — 계획 항목 5("정확한 초 단위 값은
  이번 계획에서 확정하지 않는다")와 일치.
- **코드 컨벤션 준수** — `@Value` 필드 주입은 `PaymentService`/`ProductService`/`AuthController`/
  `TeamService`에 이미 있는 기존 패턴과 일관됨(생성자 주입 원칙은 `@Autowired` 필드 금지에 대한 것이지
  `@Value`는 이 저장소에서 이미 필드 주입으로 통일돼 있음). 로깅은 ERROR 레벨로 도메인 식별자(`sessionId`,
  `errorType`) 포함해서 컨벤션(`docs/code-convention.md` 로깅 표) 준수. 트랜잭션 경계(`ChatLogRecorder`)는
  건드리지 않음.
- **OSIV `false` 전환의 회귀(LazyInitializationException) 가능성 검토** — 이 저장소는 REST 전용이라
  Thymeleaf/JSP 등 뷰 렌더링 의존성 자체가 없음(`build.gradle`에 템플릿 엔진 의존성 없음, grep으로 확인).
  컨트롤러 계층에서 `@EntityGraph`/`JOIN FETCH`로 직접 지연 로딩을 우회하는 게 아니라, 애초에 컨트롤러가
  엔티티를 직접 반환하지 않고 서비스의 `@Transactional` 메서드 안에서 DTO로 변환을 끝내는 게 이 코드베이스의
  기존 컨벤션(`docs/code-convention.md`)이라 OSIV가 켜져 있을 때 얻는 이점(트랜잭션 밖 지연 로딩 허용)을
  애초에 안 쓰고 있었다. `./gradlew test` 전체 스위트(다른 도메인 컨트롤러 테스트 다수 포함)가 이번 변경으로
  새로 실패한 테스트가 0개인 것으로 추가 검증됨(위 계산적 평가의 8개 실패는 stash 비교로 무관함이 확인된
  기존 문제).
- **로컬 진단 산출물 정리 확인** — `scratch_*`/`bootrun*.log`/`cookies.txt` 등 저장소에 남아있지 않음을
  직접 확인(Generate 주장과 일치).

### 결론

계획 문서(`docs/dev/ai/buyer-chatbot/changes/002-buyer-chatbot-concurrent-timeout-bug.md`)의 평가 기준을
전부 충족. **PASS**로 판정하고 `docs/dev/ai/buyer-chatbot/design.md`를 갱신했으며, 계획 문서를
`docs/dev/ongoing/buyer-chatbot-concurrent-timeout-bug.md`에서 `changes/002-*.md`로 채번 이동했다.

- 참고: 코드는 아직 커밋되지 않은 워킹 트리 변경 상태다(사용자가 커밋을 명시적으로 요청하지 않는 한
  Evaluate 단계에서 임의로 커밋하지 않음, `AGENTS.md` 규칙).

### 후속 (별개 작업에서 원인 데이터 제거함) — 2026-08-19

위에서 "무관한 기존 문제"로 지목한 `ProductControllerTest`/`ProductCachingTest` 실패 6건의 원인이었던
로컬 MySQL(`gong9ri_db`) 잔여 상품 데이터("블루투스 이어폰" 등, `product.id=2918~2920` +
`group_buy_team.id=704`)를 이후 별개 작업(`product/crud` 003, 가격 구간 최소 인원 검증)에서 실제로
찾아 삭제 처리함. 상세: `docs/logs/product/crud/003-price-tier-min-count-validation.md`의 Attempt 2.
현재는 `./gradlew test` 전체가 통과한다 — 이 로그를 나중에 참고할 때 "아직 해결 안 된 로컬 환경
문제"로 오해하지 않도록 남긴다.
