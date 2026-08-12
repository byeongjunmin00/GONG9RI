# 001-buyer-chatbot-frontend — 구매자 챗봇 프론트엔드 (로그)

## Attempt 1 — 2026-08-12

- 시도: `docs/dev/ongoing/buyer-chatbot-frontend.md`에 승인된 계획대로 구매자 챗봇 플로팅 위젯 프론트엔드를 신규 구현했다. 백엔드(`BuyerChatController`/`BuyerChatService` 등)는 전혀 건드리지 않았다.
  1. **`js/header-auth.js` 확장**: 기존 `applyLoggedInState`/`bindLogout`/헤더 토글 로직은 그대로 두고, `init()`의 `/auth/me` 호출 `.then`(성공)과 `.catch`(실패) 각 분기 끝에 `document.dispatchEvent(new CustomEvent('gong9ri:auth-resolved', { detail: { loggedIn, member } }))`을 추가했다. 성공 시 `loggedIn: true, member: member`, 실패 시 `loggedIn: false, member: null`.
  2. **`partials/chat-widget.html` 신규**: 최상위 `<div id="chat-widget" class="chat-widget" hidden>` 안에 토글 버튼(`#chat-widget-toggle`)과 대화 패널(`#chat-widget-panel`, 기본 별도로 `hidden`)을 담았다. 패널 안에 헤더(제목+닫기 버튼), 메시지 목록(`#chat-widget-messages`, `role="log" aria-live="polite"`), 입력 폼(`#chat-widget-form` → 입력창 `#chat-widget-input` + 전송 버튼 `#chat-widget-send`)을 배치했다.
  3. **`js/chat-widget.js` 신규**:
     - `gong9ri:auth-resolved` 이벤트를 구독해 `detail.loggedIn && detail.member.role === 'BUYER'`일 때만 `#chat-widget`의 `hidden`을 해제하고, 그 외에는 명시적으로 `hidden = true`로 되돌린다(중복 발행 대비 방어적으로 매번 판정).
     - 토글 버튼 클릭 시 패널을 열고 닫는다. 패널을 **처음** 열 때만(`historyLoaded` 플래그) `sessionStorage.getItem('gong9ri_chat_session_id')`가 있으면 `GET /api/buyer/chat/sessions/{id}/messages`를 `credentials: 'same-origin'`으로 호출해 이력을 role별(`ASSISTANT`→어시스턴트 정렬, 그 외→사용자 정렬) 말풍선으로 렌더링한다. 실패(4xx 등 `res.ok === false`, 또는 payload 형식이 배열이 아닌 경우)하면 콘솔에만 로그를 남기고 `sessionStorage`에서 세션ID를 지운 뒤 조용히 새 대화로 취급한다(사용자에게 에러 표시 없음).
     - 메시지 전송(`handleSubmit`): 입력값이 공백만이거나 비어있으면 아무 것도 하지 않는다. 전송 시작 시 사용자 말풍선을 즉시 `textContent`로 추가하고 입력창을 비운 뒤 `setSending(true)`로 입력창·전송 버튼을 `disabled`로 만든다. `POST /api/buyer/chat/messages`를 `credentials: 'same-origin'`, `Content-Type: application/json`으로 호출하며 바디는 `{content}` + (세션ID가 있으면) `{sessionId: Number(currentSessionId)}`(서버 `Long` 필드에 맞춰 숫자로 변환). `res.ok`가 false면 `res.json()`으로 에러 파싱(실패 시 fallback 문구) 후 `chat-widget__message--system` 말풍선으로 안내하고 재활성화, `res.ok`면 `readStream(res)`으로 넘어간다. `fetch` 자체가 reject되면(네트워크 단절 등) 동일하게 시스템 메시지 안내 후 재활성화.
     - `readStream(response)`: `response.body.getReader()` + `TextDecoder('utf-8', {stream:true})`로 청크를 이어붙이며 버퍼를 `'\n\n'` 기준으로 분리해 완결된 이벤트 블록만 처리하고(마지막 미완성 조각은 버퍼에 남김), 각 블록을 줄 단위로 나눠 `event:`/`data:` 접두사를 파싱한다(`docs/api/chat.md` 명시 포맷 그대로). `session` → `currentSessionId` 갱신 + `sessionStorage.setItem`, `message` → 어시스턴트 말풍선이 없으면 새로 만들고 있으면 `textContent`에 이어붙임(스트리밍처럼 보이게), `done` → `setSending(false)`. 스트림이 `done: true`(reader 종료)로 끝났는데 `done` 이벤트를 못 받은 경우를 대비해 안전망으로 한 번 더 `setSending(false)`를 호출한다.
     - 전체 파일에서 `innerHTML`을 전혀 사용하지 않았다 — 사용자 입력, 이력 조회 결과, SSE로 받은 텍스트, 에러 메시지 전부 `el.textContent = ...` 또는 `el.textContent += ...`로만 DOM에 반영했다.
  4. **`css/components.css` 위젯 스타일 추가**: 플로팅 버튼(`position: fixed`, 우하단), 패널(`position: fixed`, 카드형 342px 폭, 헤더/메시지 목록(flex-1, overflow-y auto)/입력 폼 3단 구성), 말풍선 좌(assistant)/우(user)/중앙(system) 정렬. 이 세션에서 4번 겪은 `[hidden]` specificity 버그(`.btn`, `.product-detail`, `.revenue-cards`, `.site-header__auth`와 동일 패턴)를 피하려고 `.chat-widget[hidden]`, `.chat-widget__button[hidden]`, `.chat-widget__panel[hidden]`에 각각 `{ display: none; }` 명시 보정 규칙을 추가했다(`#chat-widget` 자체는 원래 `display`를 선언하지 않지만 — 자식이 전부 `position: fixed`라 부모 레이아웃과 무관 — 방어적으로 동일 패턴을 맞췄다).
  5. **10개 페이지에 위젯 삽입**: `index.html`, `login.html`, `signup.html`, `product.html`, `checkout.html`, `seller/products/new.html`, `seller/products/edit.html`, `seller/mypage.html`, `buyer/mypage.html`, `design-system.html` 전부에 `<div data-include="footer"></div>` 다음 줄에 `<div data-include="chat-widget"></div>`를, `<script src="/js/header-auth.js"></script>` 다음 줄에 `<script src="/js/chat-widget.js"></script>`를 추가했다(페이지 전용 스크립트보다 먼저 로드되도록). `product.html`은 기존 STOMP CDN 스크립트/`product.js`보다 앞에 배치했다. 기존 헤더/푸터 include, 페이지 전용 스크립트 로직은 전혀 건드리지 않았다.
  - 사전 확인: `docs/api/README.md`로 공통 응답 포맷이 `{success, data}` 래핑임을 확인해 이력 조회 응답 파싱 시 `json.data`를 꺼내도록 했고, `SecurityConfig`에 `/partials/**`가 이미 `permitAll`인 것을 확인해 신규 partial 경로에 별도 보안 설정이 필요 없음을 확인했다(백엔드 변경 없음 원칙 준수). `AuthController`/`MemberResponse`를 확인해 `/auth/me` 응답의 `role` 필드가 `"BUYER"`/`"SELLER"` 문자열임을 확인하고 `member.role === 'BUYER'` 비교가 기존 헤더 nav의 `data-role="BUYER"` 패턴과 동일하게 동작함을 검증했다.
  - `./gradlew compileJava` 실행 — `BUILD SUCCESSFUL`(Java 코드 변경이 없어 `UP-TO-DATE`, 회귀 없음 확인용).

- 결과: **PASS**

- 원인: 승인된 계획(`docs/dev/ongoing/buyer-chatbot-frontend.md`)대로 프론트 전용 범위 안에서 구현이 이루어졌고, 계산적 평가(빌드/테스트)와 추론적 평가(계획·컨벤션 대조) 모두 이상 없음을 확인했다. (평가 과정에서 CSS 특이도를 한 차례 잘못 계산해 `[hidden]` 규칙이 무효화된다고 오판했다가, 재계산 후 정정했다 — 아래 2번 항목에 정정 경위를 남긴다.)

  1. **계산적 평가**
     - `docker ps` — MySQL(`gong9ri-main-mysql-1`), Redis(`gong9ri-main-redis-1`) 둘 다 `healthy` 상태로 이미 가동 중 확인.
     - `./gradlew compileJava` — `BUILD SUCCESSFUL` (`:compileJava UP-TO-DATE`, 이번 작업은 정적 리소스만 변경이라 Java 컴파일 자체에 영향 없음).
     - `./gradlew test` — `BUILD SUCCESSFUL`, `build/test-results/test/*.xml` 26개 전부 `failures="0" errors="0"` 확인(전수 grep). `BuyerChatControllerTest`/`BuyerChatServiceTest`(구매자 챗봇 백엔드) 포함 전체 통과 — 프론트 변경으로 인한 회귀 없음.
     - **스코프 밖 별도 관찰(이번 판정에 미반영)**: 병렬 진행 중인 `policy-rag-boot-decoupling` 작업의 `PolicyDocumentIndexer.java`/`PolicyDocumentIndexerTest.java`도 이번 실행 시점 기준 `BUILD SUCCESSFUL`에 포함되어 통과 상태였다(테스트 실패나 컴파일 에러 없었음). 다만 이 파일들은 이번 챗봇 프론트 평가 대상이 아니므로 별도 언급만 하며 판정에 사용하지 않았다.

  2. **추론적 평가 (계획 대조, 파일 직접 읽음 + `git diff`)**
     - `js/header-auth.js`: `git diff` 확인 결과 순수 추가만 있고(+9줄), 기존 `applyLoggedInState`/`bindLogout`/`init()`의 헤더 토글 로직은 한 글자도 바뀌지 않았다. `/auth/me` 성공(`.then`)과 실패(`.catch`) 두 분기 모두 끝에서 `document.dispatchEvent(new CustomEvent('gong9ri:auth-resolved', { detail: {...} }))`을 발행한다(성공: `{loggedIn:true, member}`, 실패: `{loggedIn:false, member:null}`) — 계획과 정확히 일치.
     - `partials/chat-widget.html`: 최상위 `#chat-widget`이 기본 `hidden` 속성을 가지며, 토글 버튼(`#chat-widget-toggle`) + 패널(`#chat-widget-panel`, 별도 `hidden`) 구조 안에 헤더/닫기버튼, 메시지 목록(`#chat-widget-messages`), 입력 폼(`#chat-widget-form` → 입력창+전송버튼)이 모두 존재. 계획서의 "버튼+패널+메시지목록+입력창" 구조와 일치.
     - `js/chat-widget.js`:
       - `gong9ri:auth-resolved` 구독(`handleAuthResolved`)에서 `detail.loggedIn && detail.member.role === 'BUYER'`일 때만 `widgetEl.hidden = false`, 그 외에는 `widgetEl.hidden = true`로 **명시적으로 되돌리는 로직**이 있다(계획보다 한 단계 더 방어적 — 그 외엔 계속 숨김 요건 충족).
       - 세션 복원: `sessionStorage.getItem('gong9ri_chat_session_id')`로 저장된 세션ID를 패널 첫 오픈 시(`historyLoaded` 플래그) 확인하고, `GET /api/buyer/chat/sessions/{id}/messages`로 이력을 불러온다. 실패 시(`res.ok` false, 또는 `json.data`가 배열이 아님) 콘솔 로그만 남기고 `sessionStorage`를 지운 뒤 조용히 새 대화로 전환 — 계획서의 "실패해도 에러 배너 없이 새 세션 취급"과 일치. `sessionId` 갱신 시(`saveSessionId`)도 `sessionStorage.setItem` 사용 확인.
       - SSE 파싱: `readStream()`이 `response.body.getReader()` + `TextDecoder('utf-8', {stream:true})`로 청크를 받아 버퍼를 `'\n\n'` 기준으로 나누고, 완결 블록만 처리(미완성 조각은 버퍼에 유지)한다. 각 블록을 줄 단위로 나눠 `event:` 접두사는 `trim()`, `data:` 접두사는 **trim하지 않고 그대로** 이어붙인다 — `docs/api/chat.md`의 실제 예시(`event:message` 다음 줄이 `data: 감귤 상품을...`처럼 콜론 뒤 공백이 컨텐츠의 일부로 포함된 케이스)와 정확히 맞는 처리다(임의로 공백을 없애는 스펙 오독이 없음). `session`→`saveSessionId`, `message`→어시스턴트 말풍선에 `textContent +=`, `done`→`setSending(false)`. reader가 `done:true`로 끝났는데 `done` 이벤트를 못 받은 경우의 안전망(`pump()` 내 추가 `setSending(false)`)까지 있어 계획보다 견고하다.
       - 에러 처리: 스트림 시작 전 4xx는 `res.ok` 체크 후 `res.json()`으로 에러 메시지 파싱(실패 시 폴백 문구) → 시스템 말풍선 안내 + 재활성화. `fetch` 자체 실패(네트워크 단절)도 `.catch`에서 동일하게 처리. 계획서의 "네트워크 에러/시작 전 4xx는 채팅창 안 시스템 메시지로 안내" 요건 충족.
       - `grep -n "innerHTML" js/chat-widget.js` 결과 주석(설명 문구) 안에 문자열로만 등장하고 실제 코드에서는 전혀 쓰이지 않음(`appendMessage`, `loadHistory` 렌더링 전부 `el.textContent = ...`/`+=`) — `innerHTML` 미사용 요건 충족.
     - `css/components.css`: `.chat-widget[hidden]`, `.chat-widget__button[hidden]`, `.chat-widget__panel[hidden]` 세 규칙 모두 `{ display: none; }`으로 명시 보정되어 있음을 직접 확인. 처음에는 이 보정 규칙이 각 요소의 일반 `display` 규칙(`.chat-widget__button{display:inline-flex}`, `.chat-widget__panel{display:flex}`)보다 CSS 파일 내에서 **앞서** 위치한다는 이유로 "동일 specificity면 소스상 나중 규칙이 이긴다"는 일반론을 적용해 `[hidden]`이 무시될 것으로 잘못 판단했었다. **재계산 결과 이는 평가자(나)의 오판이었다**: `.chat-widget__button[hidden]`은 클래스 선택자(`.chat-widget__button`)와 속성 선택자(`[hidden]`)가 결합된 컴파운드 셀렉터라 특이도가 (0,2,0)이고, 단일 클래스 선택자 `.chat-widget__button`은 (0,1,0)이다. (0,2,0) > (0,1,0)이므로 **소스 순서와 무관하게 `[hidden]` 보정 규칙이 항상 이긴다** — "동일 specificity"라는 전제 자체가 틀렸다(속성 선택자 하나가 추가되면 클래스 선택자와 합산되어 특이도가 올라간다는 점을 빠뜨렸다). `.chat-widget[hidden]`도 마찬가지로 (0,2,0)이라 문제 없다.
     - **기존 코드베이스 패턴과 대조로 재확인**: `grep -n '\[hidden\]' css/*.css` 결과 이 저장소에는 이미 `.btn[hidden]`(6줄, `.btn{display:inline-flex}`인 10줄보다 **먼저** 위치), `.product-detail[hidden]`(318줄), `.site-header__auth[hidden]`(478줄), `.revenue-cards[hidden]`(670줄)이 전부 동일하게 "보정 규칙이 일반 규칙보다 먼저 오는" 순서로 이미 존재하고 있다 — 즉 지금 챗봇 위젯 CSS가 따른 순서·패턴은 이 저장소에서 이미 검증되어 쓰이고 있는 기존 관례와 정확히 같다. 컴파운드 셀렉터가 특이도로 이기는 구조이기 때문에 순서에 상관없이 항상 안전하다.
     - 결론: `[hidden]` 보정은 계획서 요건대로 정확히 구현되어 있고 실제로도 유효하다. 판매자/비로그인 상태에서 버튼이 숨겨지고, 패널을 닫으면 실제로 사라지는 동작에 CSS 특이도 문제는 없다.

  3. **10개 페이지 스크립트/include 태그 검증**: `grep -c` 전수 확인 결과 `index.html`, `login.html`, `signup.html`, `product.html`, `checkout.html`, `seller/products/new.html`, `seller/products/edit.html`, `seller/mypage.html`, `buyer/mypage.html`, `design-system.html` 10개 전부 `data-include="chat-widget"` 1회, `<script src="/js/chat-widget.js">` 1회씩만 존재(중복/누락 없음). `git diff`로 각 페이지 삽입 위치도 계획대로(`footer` include 다음, `header-auth.js` 스크립트 다음이자 페이지 전용 스크립트 이전) 확인.
  4. **페이지 전용 스크립트 불변경 확인**: `git diff --stat` 결과 `main.js`/`product.js`/`checkout.js`/`seller-mypage.js`/`buyer-mypage.js`/`seller-product-new.js`/`seller-product-edit.js`는 변경 목록에 전혀 없음(diff 대상 파일 14개 중 이 7개는 없음) — HTML의 스크립트 태그 위치만 바뀌었을 뿐 JS 파일 자체는 손대지 않았다는 로그 진술과 일치.
  5. **백엔드 챗봇 코드 불변경 확인**: `git diff --stat`에 `BuyerChatController.java`/`BuyerChatService.java`가 전혀 나타나지 않음 — 백엔드 미변경 확인.

- **판정: PASS**

  계획 대비 벗어난 점 없음. 계산적 평가(`compileJava`/`test` 전부 `BUILD SUCCESSFUL`, 회귀 없음), 추론적 평가(계획 문서의 모든 항목 — 인증 이벤트 발행, 위젯 기본 숨김/역할 게이팅, 세션 복원, SSE 파싱 포맷, 에러 처리, `innerHTML` 미사용, `[hidden]` 보정, 10개 페이지 스크립트 태그 1회씩, 페이지 전용 스크립트·백엔드 챗봇 코드 불변경) 모두 충족을 코드 직접 확인 + `git diff`로 검증했다.

- 증거(API 관련 아님, 코드 발췌 — CSS 특이도 재검증):
  ```
  6    .btn[hidden] { display: none; }         /* 특이도 (0,2,0) — 기존 관례 */
  10   .btn { display: inline-flex; ... }      /* 특이도 (0,1,0) — [hidden] 규칙보다 낮아 항상 짐 */

  498  .chat-widget[hidden] { display: none; }         /* (0,2,0) */
  502  .chat-widget__button[hidden] { display: none; } /* (0,2,0) */
  506  .chat-widget__button { ... display: inline-flex; ... } /* (0,1,0) — 항상 [hidden]에 짐 */
  531  .chat-widget__panel[hidden] { display: none; }  /* (0,2,0) */
  535  .chat-widget__panel { ... display: flex; ... }  /* (0,1,0) — 항상 [hidden]에 짐 */
  ```
  즉 신규 챗봇 위젯의 `[hidden]` 보정은 저장소에 이미 존재하는 `.btn[hidden]` 등과 동일한, 검증된 안전한 패턴을 그대로 따르고 있다.

## Attempt 2 — 2026-08-12 (평가 기준의 브라우저 수동 확인, 실제 OpenAI 키 사용)

- 시도:
  - 도커 MySQL/Redis + `bootRun`으로 구매자(`chatbuyer1`)/판매자(`chatseller1`) 계정 생성. 실제 `OPENAI_API_KEY`가 로컬 프로세스 환경에 설정돼 있어(사용자 확인) 로컬에서 실제 LLM 응답까지 검증 가능했다(폴백 경로가 아니라 진짜 답변 확인).
  - 비로그인/구매자/판매자 3가지 상태에서 위젯 노출 여부 확인 → 구매자로 패널을 열어 "감귤 상품 있나요?" 전송 → 응답 확인 → 다른 페이지(`product.html`)로 이동 후 패널을 다시 열어 이력 복원 확인 → 후속 질문("방금 내가 뭐라고 물어봤지?")으로 멀티턴 확인 → 로그아웃 후 판매자 계정으로 재확인. 확인 후 테스트 계정·세션·메시지·로그 전부 정리.
- 결과: ✅ **PASS** (버그 없음, 실제 LLM 파이프라인 전체 정상 동작)
- 원인: (해당 없음)
- 증거:
  - **비로그인**: `#chat-widget.hidden === true`.
  - **구매자 로그인**: `hidden === false`, `display: "block"` — 위젯 실제로 보임.
  - **실제 메시지 전송·Tool Calling**: "감귤 상품 있나요?" 전송 → 어시스턴트 응답 "현재 GONG9RI에 등록된 감귤 상품은 없습니다. 다른 상품을 검색해 보시겠어요?"(이 시점 DB에 감귤 상품이 실제로 없었으므로 Tool Calling으로 실시간 조회한 정확한 답변임을 확인). `chat_interaction_log` DB 조회로 실제 API 호출 확인: `model=gpt-4o-mini`, `success`, `total_tokens=971`, `latency_ms=3463`, `error_type=NULL`.
  - **세션 유지·이력 복원**: `sessionStorage.gong9ri_chat_session_id`에 실제 세션ID 저장 확인 → `product.html`로 이동 후 패널을 다시 열자 `GET /api/buyer/chat/sessions/{id}/messages`로 직전 대화(질문+답변)가 그대로 복원됨.
  - **멀티턴**: 이어서 "방금 내가 뭐라고 물어봤지?" 전송 → "당신은 \"감귤 상품 있나요?\"라고 물어보셨습니다. GONG9RI에 등록된 감귤 상품은 없다고 답변드렸습니다. 추가로 궁금한 점이 있으신가요?" — 이전 턴을 정확히 참조한 응답으로 멀티턴 컨텍스트가 실제로 유지됨을 확인.
  - **전송 중 UX**: 전송 직후 `#chat-widget-input`/`#chat-widget-send` 모두 `disabled=true`, 응답(`done` 이벤트) 후 `disabled=false`로 정상 복귀.
  - **판매자 계정**: 로그아웃 후 판매자로 재로그인 시 `#chat-widget.hidden === true`, `display: "none"` — 완전히 숨겨짐 재확인.
  - **모바일(375×812)**: `scrollWidth === clientWidth`(가로 스크롤 없음).
  - **콘솔**: 이번 챗봇 플로우로 인한 새 에러 없음(남아있던 401/404는 무관한 다른 테스트의 잔여 메시지 — 예: 존재하지 않는 productId로 이동 테스트).
  - 평가 종료 후 테스트 계정 2개, `chat_session`/`chat_message`/`chat_interaction_log` 관련 행 전부 정리 완료.
