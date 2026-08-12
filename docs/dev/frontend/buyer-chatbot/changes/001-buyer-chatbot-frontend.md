# 구매자 챗봇 프론트엔드 (frontend/buyer-chatbot)

대상: frontend/buyer-chatbot
담당: 전용운

## 배경 / 요구

`docs/dev/ai/buyer-chatbot/design.md`(민병준 담당)에서 백엔드가 이미 완성돼 있다: Tool Calling(상품 검색/내 공구 참여 조회) + SSE 스트리밍 + 멀티턴 세션 + 장애격리(타임아웃/API장애 폴백) + 토큰·지연 로깅. API 계약은 `docs/api/chat.md`에 확정돼 있다. **하지만 이 기능을 호출할 프론트엔드가 전혀 없다** — `src/main/resources/static/`에 `chat` 관련 파일이 하나도 없음(코드로 확인). 사용자 요청: 이 챗봇의 프론트를 만든다.

## 코드 확인으로 파악한 사실

- `docs/api/chat.md`:
  - `POST /api/buyer/chat/messages`(구매자 전용) — body `{sessionId?, content}`, 응답은 JSON이 아니라 `text/event-stream` SSE. 이벤트 3종: `session`(세션ID, 1회) / `message`(텍스트 조각, 여러 번) / `done`(종료, 1회). 스트림 시작 전 에러(400/401/403/404)는 일반 JSON, 시작 후 LLM 장애는 에러가 아니라 `message` 이벤트로 자연어 폴백 문구가 온다 — **클라이언트가 성공/실패를 구분할 필요 없이 `message` 이벤트를 그대로 이어붙이면 된다.**
  - `GET /api/buyer/chat/sessions/{id}/messages` — 대화 이력(본인 세션만).
  - `GET /api/buyer/chat/sessions/{id}/usage`, `GET /api/chat/stats` — 비용 대시보드용, 이번 프론트 범위에서는 쓰지 않는다(설계 문서에도 "별도 프론트 대시보드 UI는 스코프 밖"이라 명시).
- **네이티브 `EventSource`는 못 쓴다**: `EventSource`는 GET 전용이라 `sessionId`/`content`를 body로 보내는 이 POST 스트리밍 API를 못 받는다. `fetch()` + `response.body.getReader()`로 직접 SSE 텍스트를 파싱해야 한다.
- **역할 게이팅**: 구매자 전용 기능이라(판매자 403) 로그인한 역할이 `BUYER`일 때만 위젯을 보여줘야 한다. `js/header-auth.js`가 이미 `GET /api/auth/me`로 로그인 상태·역할을 알고 있다 — 이 결과를 재사용하기 위해 `header-auth.js`가 로그인 상태 확인이 끝나는 시점(성공/실패 모두)에 `document`에 `gong9ri:auth-resolved` 커스텀 이벤트(`detail: {loggedIn, member}`)를 새로 발행하도록 확장하고, 신규 챗봇 위젯 스크립트가 이 이벤트를 구독한다(중복으로 `/auth/me`를 또 호출하지 않는다).
- **`js/include.js`는 완전히 범용적**: `data-include="아무이름"` 컨테이너를 자동으로 처리하므로(코드 확인), `partials/chat-widget.html`을 새로 만들어 `data-include="chat-widget"`으로 넣으면 `include.js` 자체는 수정할 필요가 없다(`header-auth.js`만 이벤트 발행 확장).
- **`js/api.js`는 SSE에 안 맞다**: 기존 `Api.get/post`는 JSON 응답을 가정하는 래퍼라(코드 확인) 이번 SSE 호출에는 재사용하지 않고 `fetch`를 직접 쓴다. 단, `credentials: 'same-origin'`은 기존 관례를 그대로 따른다.
- `docs/dev/ongoing/`에 다른 진행 중 작업 없음(중복 없음). `docs/dev/ai/buyer-chatbot/`는 이미 Evaluate까지 통과해 `changes/001-buyer-chatbot.md`로 채번 이동된 완료 상태 — 이번 작업은 그 백엔드 코드를 전혀 건드리지 않는다.

## 설계

### 1. 배치 방식 — 전 페이지 플로팅 위젯

- 별도 페이지가 아니라 **모든 페이지 우하단에 떠 있는 채팅 버튼 → 클릭 시 패널이 펼쳐지는 위젯** 형태로 만든다(구매자가 어느 페이지에 있든 바로 물어볼 수 있게, 헤더의 로그인 상태 연동과 같은 사상).
- 로그인 상태가 `BUYER`로 확인될 때만 버튼을 노출한다(비로그인/`SELLER`는 완전히 숨김 — 헤더 nav와 달리 이 기능은 애초에 구매자 외에는 호출 자체가 불가능한 전용 기능이라 "역할 무관 항상 노출, 서버가 사후 판정" 원칙을 적용하지 않는다. 판매자가 굳이 열어봤자 403만 받을 뿐이라 노출할 이유가 없다는 판단 — 이견 있으면 확인 필요).
- 헤더 파트셜을 쓰는 페이지 전부(`index.html`, `login.html`, `signup.html`, `product.html`, `checkout.html`, `seller/products/new.html`, `seller/products/edit.html`, `seller/mypage.html`, `buyer/mypage.html`, `design-system.html`)에 `<div data-include="chat-widget"></div>` 컨테이너와 `<script src="/js/chat-widget.js">`(header-auth.js 이후 로드)를 추가한다.

### 2. 신규 파일

- `src/main/resources/static/partials/chat-widget.html` — 플로팅 버튼 + 패널(헤더/메시지 목록/입력창) 마크업. 기본 상태는 `hidden`(로그인 확인 전에는 아무것도 안 보임 — 비로그인/판매자로 판명되면 계속 숨김 유지).
- `src/main/resources/static/js/chat-widget.js`:
  - `gong9ri:auth-resolved` 이벤트 구독 → `loggedIn && member.role === 'BUYER'`일 때만 위젯을 노출.
  - 버튼 클릭 시 패널 토글. 패널을 처음 열 때, `sessionStorage`에 저장된 `sessionId`가 있으면 `GET /api/buyer/chat/sessions/{id}/messages`로 이전 대화를 불러와 렌더링(실패하면 조용히 새 세션으로 취급 — 에러 배너 띄우지 않음, 대화 이력은 있으면 좋은 정도의 편의 기능이라 실패해도 새로 시작하면 그만).
  - 메시지 전송: `fetch('/api/buyer/chat/messages', {method:'POST', credentials:'same-origin', headers:{'Content-Type':'application/json'}, body: JSON.stringify({sessionId, content})})` → 시작 전 에러(4xx)는 JSON으로 오므로 `res.ok` 체크 후 처리, 성공하면 `response.body.getReader()`로 직접 SSE 텍스트 파싱(`event:`/`data:` 줄 단위) → `session` 이벤트로 `sessionId` 갱신(`sessionStorage`에도 저장) → `message` 이벤트마다 어시스턴트 말풍선에 텍스트를 이어붙임(스트리밍처럼 보이게, `textContent` 이어붙이기만 사용) → `done`에서 입력창 재활성화.
  - 사용자 메시지·서버에서 온 텍스트 모두 `textContent`로만 DOM에 반영(XSS 방지, `innerHTML` 금지 — 이 세션 전체의 원칙과 동일).
  - 전송 중 입력창/전송 버튼 비활성화(중복 전송 방지), 완료 시 재활성화.
  - 네트워크 에러(fetch 자체 실패)나 스트림 시작 전 4xx 에러는 채팅창 안에 시스템 메시지 형태로 안내(예: "메시지를 보내지 못했습니다. 잠시 후 다시 시도해주세요.") — 페이지 레벨 알림(`#page-alert` 등) 재사용은 하지 않는다(위젯이 페이지마다 다른 구조라 결합시키지 않는 게 안전).
- `src/main/resources/static/js/header-auth.js` 수정: `init()`의 `/auth/me` 호출 성공/실패 각 분기에서 `document.dispatchEvent(new CustomEvent('gong9ri:auth-resolved', {detail: {...}}))`을 추가로 발행한다. 기존 헤더 토글 로직(`applyLoggedInState`)은 그대로 둔다.
- `css/components.css`에 위젯 스타일(플로팅 버튼, 패널, 메시지 말풍선 좌우 정렬) 추가. **`hidden` 속성을 쓰는 위젯 컨테이너는 이 세션에서 세 번 겪은 specificity 버그를 피하기 위해, 위젯 자체 `display`를 커스텀 클래스에서 직접 선언하지 않거나(`.form-alert`류 패턴) `.클래스[hidden]{display:none}` 보정 규칙을 함께 추가한다.**

### 3. 세션 유지 범위

- `sessionStorage`(탭 단위, 브라우저 종료 시 소멸)에 `sessionId`를 저장해 같은 탭에서 페이지를 이동해도 대화가 이어지게 한다. 새 탭/새 세션에서는 새 대화로 시작(서버도 30분 미사용 시 자동 만료되므로 이 정도 범위면 충분하다고 판단).

## 태스크

- [ ] `js/header-auth.js` — `gong9ri:auth-resolved` 이벤트 발행 추가(로그인/비로그인 양쪽 분기)
- [ ] `partials/chat-widget.html` — 플로팅 버튼 + 패널 마크업(기본 hidden)
- [ ] `js/chat-widget.js` — 인증 이벤트 구독, 열기/닫기, 이력 복원, SSE 파싱·전송, 세션 저장
- [ ] `css/components.css` — 위젯 스타일(+ `[hidden]` 보정 필요 시 포함)
- [ ] 헤더 파트셜을 쓰는 10개 페이지에 `data-include="chat-widget"` 컨테이너 + `<script src="/js/chat-widget.js">` 추가

## 평가(통과) 기준

- `./gradlew test` 전체 통과(백엔드 코드는 건드리지 않으므로 회귀 없어야 함).
- `./gradlew bootRun` 후 브라우저 실측(실제 OpenAI 키가 없는 환경이면 LLM 응답 대신 API 장애 폴백 문구가 오는 것으로 "스트리밍 파이프라인 자체가 동작하는지"를 검증한다 — 실제 정답 품질 검증은 이번 범위 밖):
  - 비로그인/`SELLER` 로그인 상태에서는 어느 페이지에서도 챗봇 버튼이 보이지 않는다.
  - `BUYER` 로그인 상태에서는 버튼이 보이고, 클릭하면 패널이 열린다.
  - 메시지를 보내면 입력창이 비활성화되고, `session`/`message`/`done` 이벤트가 정상 처리되어(콘솔·Network 탭 확인) 어시스턴트 말풍선에 텍스트(실키 없으면 폴백 안내 문구)가 표시된 뒤 입력창이 다시 활성화된다.
  - 같은 탭에서 다른 페이지로 이동해도 챗봇 패널을 다시 열면 이전 대화가 이어져 있다(`sessionId` 유지 확인).
  - 판매자 계정으로 억지로 API를 직접 호출하면(위젯 자체는 안 보이지만) 403이 오는 것은 기존 백엔드 동작 그대로(이번 프론트 작업이 새로 막을 필요 없음, 위젯 비노출로 충분).
  - (코드 리뷰) 사용자 입력·서버 응답 텍스트가 `textContent`로만 DOM에 반영되는지(`innerHTML` 미사용).

## 리스크 / 전제

- **실제 OpenAI 키 없이는 답변 품질을 검증할 수 없다** — 이번 프론트 작업의 평가는 "파이프라인이 붙어서 동작하는지"까지이고, 실제 유용한 답변이 오는지는 별도로 키가 있는 환경에서 확인이 필요하다(이미 백엔드 단계에서 실제 호출로 검증됨, `docs/logs/ai/buyer-chatbot/001-buyer-chatbot.md` 참고).
- **`fetch` 기반 수동 SSE 파싱**: 브라우저 네이티브 `EventSource`보다 직접 구현 부담이 있고, 프록시/네트워크 환경에 따라 스트리밍 청크 분할이 달라질 수 있다 — 이번 구현은 `docs/api/chat.md`에 명시된 정확한 포맷(각 이벤트가 `event:`/`data:` 두 줄 + 빈 줄)을 전제로 파싱한다.
- **위젯 비노출 = 접근 제어 아님**: 판매자 계정이 개발자도구로 API를 직접 호출하면 여전히 가능하나 서버가 403으로 막는다(기존 백엔드 책임) — 위젯을 숨기는 것은 UX 편의일 뿐 보안 경계가 아니라는 점은 헤더 nav와 동일한 원칙.
- **대화 이력 복원 실패는 조용히 무시**: 만료되거나 존재하지 않는 `sessionId`가 `sessionStorage`에 남아있어도 에러를 사용자에게 보여주지 않고 새 대화로 넘어간다(설계에 명시).

## 문서 산출물

- 이 계획 문서: `docs/dev/ongoing/buyer-chatbot-frontend.md`
- 신규 API 명세 없음(기존 `docs/api/chat.md` 그대로 사용).
- Evaluate 통과 시 `docs/dev/frontend/buyer-chatbot/design.md` 신규 작성 + 이 ongoing 문서를 `docs/dev/frontend/buyer-chatbot/changes/001-buyer-chatbot-frontend.md`로 채번 이동.
