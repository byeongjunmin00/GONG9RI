# 브라우저용 에러 페이지 (frontend/error-page) — Design

## 개요

주소창에 오타를 내고 들어오거나 인증이 필요한 경로에 직접 접근했을 때, 사람이 브라우저에서 **날 JSON을 그대로 보던 문제**를 고친다. 상태 코드와 인가 규칙은 그대로 두고 **응답의 표현만** 나눈다.

## 배경 — 무엇이 잘못돼 있었나

```
GET /nonexistent-path
→ 401 {"success":false,"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}
```

두 가지가 문제였다.

1. **날 JSON이 사용자 화면에 그대로 노출된다.**
2. **안내가 사실과 다르다.** 없는 주소인데 "로그인이 필요합니다"라고 말한다.

401이 나온 이유는 이렇다 — `SecurityConfig`의 permitAll 목록은 `/`, `/*.html`, `/**/*.html`, `/css/**` 등 **확장자·접두사 기반**이라, 확장자 없는 경로(`/nonexistent-path`, `/product`)는 어디에도 안 걸려 `anyRequest().authenticated()`에 막힌다. **핸들러에 도달하기 전에 거절되므로 404가 될 기회조차 없다.**

## 설계 결정

### 인가 규칙(기본 차단)은 건드리지 않는다

"`/api/**`가 아니면 전부 permitAll"로 바꾸면 확장자 없는 경로도 404가 되지만, **기본 정책이 차단에서 허용으로 뒤집힌다.** 지금은 `/api` 밖 서버 엔드포인트가 `/sitemap.xml` 하나뿐이라 당장은 안전하지만, 나중에 누군가 non-API 엔드포인트를 추가하면 **자동으로 공개되는 함정**이 된다. 보안 설정의 기본값은 차단이어야 한다고 보고 채택하지 않았다.

대신 **응답의 표현만** 나눈다 — 상태 코드(401/404)도, 어떤 경로가 permitAll인지도 그대로다.

### 브라우저와 프로그램 호출을 `Accept` 헤더로 구분한다

| 요청 주체 | `Accept` | 응답 |
|---|---|---|
| 브라우저 주소창 탐색 | `text/html,...` 포함 | `/error.html`로 리다이렉트 |
| 프론트 `fetch` | `*/*` (미지정 기본값) | **기존 JSON 그대로** |

**이 구분이 이 기능의 핵심 제약이다.** 프론트 전체가 에러 응답의 `code`/`message`를 파싱해 분기한다(로그인 배너, 결제 실패 안내 등). 응답 형태가 바뀌면 그것들이 한꺼번에 망가지므로, `fetch` 경로는 절대 건드리지 않는다.

### 404와 401을 같은 페이지로 합친다

"없는 주소"와 "권한이 없어 못 보는 주소"를 구분해서 알려주면 **어떤 경로가 실재하는지 외부에 알려주는 셈**이 된다. 그래서 문구도 두 가능성을 함께 안내한다 — "주소가 잘못되었거나, 로그인이 필요한 페이지일 수 있어요."

## 구현

- `static/error.html` — 헤더/푸터 partial을 포함한 일반 페이지. 검색 결과에 뜨면 안 되므로 `<meta name="robots" content="noindex">`.
- `common/web/BrowserRequests.isBrowserNavigation(request)` — `Accept`에 `text/html`이 있는지로 판정하는 유틸.
- `ApiAuthenticationEntryPoint`(401) / `GlobalExceptionHandler.handleNoResourceFound`(404) — 브라우저 탐색이면 `/error.html`로 리다이렉트, 아니면 기존 JSON.
- `css/components.css`의 `.error-page*`.

## 검증

`ErrorPageResponseTest` 6케이스. 그중 **3건이 회귀 방지**다 — fetch가 404/401에서 기존 JSON(`NOT_FOUND`/`UNAUTHORIZED`)을 그대로 받는지, `Accept` 헤더가 아예 없는 호출도 JSON을 받는지. 프론트 에러 처리를 깨뜨리지 않는 게 이 변경에서 가장 중요한 조건이라 명시적으로 고정했다.

로컬 실서버 실측 — 브라우저 Accept로 `/nonexistent-path`·`/오타페이지.html`·`/product` 전부 `302 → /error.html`, `Accept: */*`로는 각각 기존 JSON 유지.

## 알려진 범위

- 브라우저에는 리다이렉트(302)로 응답하므로 최종 상태 코드는 200(에러 페이지)이 된다. 검색엔진 관점에서 엄밀한 404 신호는 아니지만, `noindex`로 색인을 막고 있고 우리 sitemap에는 실재하는 URL만 올라간다.
- `/api/**`의 잘못된 경로는 여전히 401 JSON이다(브라우저로 직접 열지 않는 한). 어떤 API가 실재하는지 감추는 편이 낫다고 판단해 그대로 뒀다.
