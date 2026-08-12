# 구매자 챗봇 플로팅 위젯 (frontend/buyer-chatbot) — Design

## 개요

모든 페이지 우하단에 떠 있는 AI 상담 챗봇 위젯이다. 백엔드(`docs/dev/ai/buyer-chatbot/design.md`)가 제공하는 Tool Calling + SSE 스트리밍 챗봇을 호출하는 프론트엔드로, 로그인한 역할이 `BUYER`로 확인될 때만 버튼이 노출된다(비로그인/`SELLER`는 애초에 호출 자체가 403이라 위젯을 아예 숨긴다 — 헤더 nav의 "역할 무관 노출·서버 사후판정" 원칙과 다름). 정적 HTML/CSS/JS로 동작하며 별도 페이지가 아니라 partial(`data-include="chat-widget"`)로 전 페이지에 삽입된다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── partials/chat-widget.html   # 플로팅 버튼 + 대화 패널(헤더/메시지 목록/입력 폼) 마크업, 기본 hidden
├── js/
│   ├── chat-widget.js           # 신규 — 인증 이벤트 구독, 열기/닫기, 이력 복원, SSE 파싱·전송
│   └── header-auth.js           # 기존 기능 확장 — 'gong9ri:auth-resolved' 이벤트 발행 추가
└── css/components.css           # .chat-widget* 스타일 + [hidden] 보정 규칙
```

- 헤더 파트셜을 쓰는 10개 페이지 전부에 `<div data-include="chat-widget"></div>`(footer include 다음)와 `<script src="/js/chat-widget.js"></script>`(`header-auth.js` 다음, 페이지 전용 스크립트 이전)를 추가: `index.html`, `login.html`, `signup.html`, `product.html`, `checkout.html`, `seller/products/new.html`, `seller/products/edit.html`, `seller/mypage.html`, `buyer/mypage.html`, `design-system.html`.
- 백엔드 의존: `POST /api/buyer/chat/messages`(SSE), `GET /api/buyer/chat/sessions/{id}/messages` — 상세는 `docs/api/chat.md` 참조. `GET /api/buyer/chat/sessions/{id}/usage`, `GET /api/chat/stats`는 이 위젯이 쓰지 않는다(대시보드용, 스코프 밖).

## 데이터 연동

- **역할 게이팅**: `js/header-auth.js`가 `GET /api/auth/me` 호출 성공/실패 양쪽 분기 끝에서 `document`에 `gong9ri:auth-resolved`(`detail: {loggedIn, member}`) 커스텀 이벤트를 발행한다(header-auth 자체의 헤더 토글 로직과는 무관, 중복 요청 방지용 재사용 목적). `chat-widget.js`는 이 이벤트를 구독해 `detail.loggedIn && detail.member.role === 'BUYER'`일 때만 `#chat-widget`의 `hidden`을 해제하고, 그 외에는 매번 명시적으로 다시 `hidden = true`로 되돌린다.
- **세션 유지**: `sessionStorage`(탭 단위)에 `gong9ri_chat_session_id` 키로 세션ID를 저장한다. 패널을 처음 열 때 저장된 값이 있으면 `GET /api/buyer/chat/sessions/{id}/messages`로 이전 대화를 불러와 역할(`ASSISTANT`/그 외)별 말풍선으로 렌더링한다. 실패(만료/본인 것 아님 등)해도 에러를 보여주지 않고 콘솔 로그만 남긴 뒤 `sessionStorage`를 지우고 조용히 새 대화로 시작한다.
- **메시지 전송**: `fetch('/api/buyer/chat/messages', {method:'POST', credentials:'same-origin', ...})` — 네이티브 `EventSource`는 POST 바디를 못 보내 쓸 수 없으므로 `response.body.getReader()` + `TextDecoder`로 직접 SSE 텍스트를 파싱한다. `docs/api/chat.md`의 정확한 포맷(`event:이름` 줄 + `data:내용` 줄, 빈 줄로 이벤트 구분, `data:` 뒤 공백은 trim하지 않고 그대로 컨텐츠로 취급)을 그대로 따른다.
  - `session` 이벤트 → `sessionId` 갱신 + `sessionStorage` 저장.
  - `message` 이벤트 → 어시스턴트 말풍선에 텍스트를 이어붙임(스트리밍처럼 보이게). LLM 장애 시 폴백 안내 문구도 이 이벤트로 오므로 클라이언트는 성공/실패를 구분하지 않는다(서버 계약 그대로).
  - `done` 이벤트 → 입력창/전송 버튼 재활성화. reader가 `done` 이벤트 없이 스트림 종료된 경우를 대비해 안전망으로 한 번 더 재활성화한다.
- **에러 처리**: 스트림 시작 전 4xx(`res.ok === false`)는 `res.json()`으로 에러 메시지를 파싱해 채팅창 안 시스템 말풍선으로 안내. `fetch` 자체 실패(네트워크 단절)도 동일하게 시스템 말풍선 안내. 페이지 레벨 알림(`#page-alert` 등)은 재사용하지 않는다(위젯이 페이지마다 다른 구조라 결합시키지 않음).
- 사용자 입력·이력 조회 결과·SSE로 받은 텍스트·에러 메시지 전부 `textContent`로만 DOM에 반영한다(`innerHTML` 미사용, XSS 방지).

## 규칙 / 검증

- **`[hidden]` 특이도 보정**: `.chat-widget__button`/`.chat-widget__panel`이 각각 `display: inline-flex`/`display: flex`를 선언해 네이티브 `[hidden]`이 무시될 수 있으므로, `.chat-widget[hidden]`/`.chat-widget__button[hidden]`/`.chat-widget__panel[hidden]`에 `{ display: none; }` 보정 규칙을 추가했다(`.btn[hidden]` 등 기존 패턴과 동일 — 클래스+속성 컴파운드 셀렉터라 특이도(0,2,0)가 단일 클래스 규칙(0,1,0)보다 항상 높아 소스 순서와 무관하게 항상 이긴다).
- **위젯 비노출은 접근 제어가 아니다**: 판매자 계정이 개발자도구로 API를 직접 호출하면 여전히 가능하나 서버가 403으로 막는다(기존 백엔드 책임). 위젯을 숨기는 것은 UX 편의일 뿐 보안 경계가 아니다(헤더 nav와 동일 원칙).
- **대화 이력 복원 실패는 조용히 무시**: 만료되거나 존재하지 않는 `sessionId`가 `sessionStorage`에 남아있어도 에러를 사용자에게 보여주지 않고 새 대화로 넘어간다.
- **세션 유지 범위**: `sessionStorage`(탭 단위, 브라우저 종료 시 소멸)만 쓴다. 새 탭/새 세션은 새 대화로 시작(서버도 30분 미사용 시 자동 만료).

## 관련 코드 위치

- `src/main/resources/static/partials/chat-widget.html` — 신규
- `src/main/resources/static/js/chat-widget.js` — 신규
- `src/main/resources/static/js/header-auth.js` — `gong9ri:auth-resolved` 이벤트 발행 추가(기존 헤더 토글 로직은 불변, 상세는 `docs/dev/frontend/header-auth/design.md` 참고)
- `src/main/resources/static/css/components.css` — `.chat-widget*` 스타일 + `[hidden]` 보정 3개 규칙
- 위 10개 정적 HTML 페이지 — `data-include="chat-widget"` + `<script src="/js/chat-widget.js">` 추가
- 백엔드(건드리지 않음): `docs/dev/ai/buyer-chatbot/design.md`, `docs/api/chat.md`
- 경위: `docs/dev/frontend/buyer-chatbot/changes/001-buyer-chatbot-frontend.md`, 실행 로그: `docs/logs/frontend/buyer-chatbot/001-buyer-chatbot-frontend.md`
