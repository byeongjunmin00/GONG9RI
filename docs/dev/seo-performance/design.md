# SEO 인덱싱 + 정적 자산 캐싱 — Design

## 개요

발제 범위 밖 자율 고도화. 사용자가 "우리 사이트 인덱스 걸려있나? 속도 업 되어있나?"라고 물어서 확인해보니 둘 다 안 되어 있었다 — `robots.txt`/`sitemap.xml`/meta description이 아예 없었고(검색엔진이 크롤링할 진입점 자체가 없음), 정적 리소스(css/js/images)가 브라우저 캐싱 없이 매 요청마다 재다운로드되고 있었다(실측으로 프로덕션 응답 헤더 확인).

## API / 인터페이스

- `GET /robots.txt` — 정적 파일. `Sitemap:` 줄로 sitemap 위치를 안내.
- `GET /sitemap.xml` — `SitemapController` → `SitemapService.buildSitemapXml()`. 정적 페이지(`/`, `/login.html`, `/signup.html`) + 전체 상품 상세(`/product.html?id={id}`)를 나열한 표준 sitemap XML(sitemaps.org 스키마)을 반환한다. 공통 응답 형식(`ApiResponse`)을 안 쓴다 — 크롤러가 읽는 표준 규격 그대로 응답해야 해서.

## 데이터 모델

새 테이블/컬럼 없음. `SitemapService`는 `ProductRepository.findAll()`을 그대로 재사용한다.

## 규칙 / 검증

- `robots.txt`/`sitemap.xml`은 `SecurityConfig`에 `permitAll` 추가(크롤러는 인증 정보가 없음).
- sitemap의 절대 URL은 `app.base-url`(기존 이메일 인증 링크 등에서 이미 쓰던 설정값, `EmailService`와 동일 패턴)로 만든다.
- 상품 수가 지금 규모(부트캠프 데모)를 크게 넘어서면(sitemap 표준 상한: URL 5만 개) sitemap 인덱스 파일로 분할해야 하는데, 지금은 그 상한을 신경 쓸 필요가 없어 분할하지 않는다 — 나중에 실제로 문제가 되면 그때 대응.
- 정적 자산 캐싱(`WebMvcConfig`): Spring Security의 기본 헤더 작성기가 `/css/**`, `/js/**`, `/images/**` 응답에 `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`를 붙이고 있었다(html 페이지는 원래도 이 헤더가 없었음, 정확한 내부 메커니즘까지는 확인 안 함). `WebMvcConfigurer.addResourceHandlers()`로 이 세 경로에만 `Cache-Control: public, max-age=600`(10분)을 명시적으로 덮어쓴다.
  - **왜 10분처럼 짧게 잡았나**: 이 프로젝트는 배포가 매우 잦은데(같은 날에도 여러 번) 정적 파일에 버전 붙이기(cache busting, 예: 파일명 해시)가 없다. 오래 캐싱하면 배포 직후에도 사용자가 옛 CSS/JS를 계속 보는 문제가 생긴다(오늘 헤더 UI를 여러 번 고치면서 그 스테일 캐시 문제를 실제로 몇 번 겪음). 짧게 캐싱해서 "같은 세션 안에서 페이지 이동할 때 재다운로드 방지" 정도의 실질적 이득만 취하고, 스테일 위험은 최대 10분으로 제한한다.
  - HTML 페이지(`.html`)는 원래도 캐시 헤더가 없어서(정적 리소스 핸들러 대상이 아님) 그대로 두었다 — 손댈 필요 없음.
- 관련 코드: `SitemapController`, `SitemapService`, `WebMvcConfig`, `SecurityConfig`(permitAll 2줄), `static/robots.txt`, `static/index.html`(`<meta name="description">`, `<link rel="canonical">`).
