# 프론트엔드 공통 레이아웃 & 디자인 시스템 (frontend/design-system) — Design

## 개요

GONG9RI 백엔드 REST API 위에 정적 HTML/CSS/JS 기반 프론트엔드를 얹기 위한 **공통 레이아웃 + 디자인 시스템**이다. React/Vue 등 프레임워크·빌드 도구는 쓰지 않고, `src/main/resources/static/` 하위에 정적 파일을 두어 Spring Boot 기본 정적 리소스 서빙을 그대로 사용한다. 개별 기능 페이지(메인/로그인/회원가입/상세/결제/마이페이지 등)는 이 산출물 위에서 이후 별도 작업으로 진행하며, 이번 작업은 그 개별 페이지가 재사용할 토큰·레이아웃·컴포넌트 스타일과 헤더/푸터 partial, 공통 fetch 래퍼 뼈대, 그리고 이를 눈으로 확인할 쇼케이스 페이지(`design-system.html`)까지만 다룬다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── design-system.html      # 색상 팔레트/타이포그래피/버튼/카드/폼/뱃지 쇼케이스 페이지
├── css/
│   ├── tokens.css           # 디자인 토큰(색상/타이포/스페이싱/라운드/그림자, 뱃지 시맨틱 컬러)
│   ├── base.css             # 리셋/전역 스타일, `.text-gradient` 유틸, 포커스 스타일
│   ├── layout.css           # 모바일 우선 헤더/푸터/컨테이너/카드 그리드
│   └── components.css       # 버튼(primary/secondary/ghost)/카드/뱃지/폼 컴포넌트
├── js/
│   ├── include.js           # `data-include="header|footer"` 컨테이너에 `/partials/{name}.html`을 fetch해 삽입하는 유틸
│   └── api.js                # `window.Api.get/post/put/patch/del` 공통 fetch 래퍼 (base `/api`, `credentials: 'same-origin'`, `docs/api/README.md` 공통 응답 포맷 파싱)
└── partials/
    ├── header.html           # 로고 + 내비게이션 + 로그인/회원가입 버튼 (비로그인 고정 마크업)
    └── footer.html           # 브랜드/링크/사업자 정보(placeholder) + copyright
```

- `index.html`은 이번 작업에 없다 — 와이어프레임상 `/`(메인 페이지) 자리이므로 이후 메인 페이지 작업에서 만든다.
- `js/api.js`는 아직 실제로 호출하는 개별 페이지가 없는 뼈대 상태다. 향후 개별 페이지가 그대로 재사용한다.

## 데이터 모델

신규 테이블/엔티티 없음. 뱃지 시맨틱 컬러(모집중/성사/미성사)는 `docs/db/group_buy_team.md`의 `RECRUITING/SUCCESS/FAILED` 상태값에 대응하도록 `tokens.css`에서 이름만 맞춰 정의했을 뿐, 실제 상태 연동(데이터 바인딩)은 개별 페이지 작업 범위다.

## 규칙 / 검증

- **Spring Security**: `SecurityConfig`의 `authorizeHttpRequests`에 `.requestMatchers("/", "/*.html", "/css/**", "/js/**", "/partials/**").permitAll()`이 추가되어, 비로그인 상태에서도 정적 프론트 리소스를 열람할 수 있다. 기존 `/api/auth/**`, `GET /api/products/**` permitAll 및 `anyRequest().authenticated()`는 변경 없이 유지된다.
- **헤더 로그인 상태 스코프 경계(이후 해소됨)**: 이 작업 시점엔 "현재 로그인 사용자 조회" 엔드포인트가 없어(`docs/api/auth.md`), 헤더는 비로그인 상태 기준 고정 마크업(로그인/회원가입 링크 노출)이었다. 이후 `GET /api/auth/me`가 추가되고 `frontend/header-auth` 작업에서 실제 로그인 상태 연동이 구현됐다 — 현재 상태는 `docs/dev/frontend/header-auth/design.md` 참고.
- **partial 재사용**: 페이지는 `<div data-include="header"></div>` / `<div data-include="footer"></div>`를 두고 `js/include.js`를 로드하면 헤더/푸터가 자동 삽입된다. `file://`로 직접 열면 fetch가 실패할 수 있어, 반드시 `./gradlew bootRun` 기동 후 `http://localhost:8080/...`으로 접속해야 한다.
- **디자인 방향성**: 인스타그램/인플루언서 감성(뉴트럴 베이스 + 오렌지/코럴/핑크/퍼플 웜 그라디언트 포인트, Poppins 헤딩 + Pretendard 본문, 카드 기반 그리드, 모바일 우선)이라는 정성적 목표는 자동화 기준으로 검증할 수 없어 사용자 육안 확인이 필요하다(코드 레벨 평가는 `docs/logs/frontend/design-system/001-design-system.md` 참고).

## 관련 코드 위치

- `src/main/resources/static/{css/tokens.css,css/base.css,css/layout.css,css/components.css,js/include.js,js/api.js,partials/header.html,partials/footer.html,design-system.html}` — 신규
- `src/main/java/com/gong9ri/gong9ri/config/SecurityConfig.java` — 정적 리소스 permitAll matcher 1줄 추가
