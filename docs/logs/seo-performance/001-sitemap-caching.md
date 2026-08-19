# 001-sitemap-caching — SEO 인덱싱 + 정적 자산 캐싱 (로그)

## Attempt 1 — 2026-08-19  ✅ PASS

- 시도: `SitemapService`/`SitemapController`로 `GET /sitemap.xml` 신규(정적 페이지 3개 + 전체 상품 상세), `static/robots.txt` 신규, `SecurityConfig`에 permitAll 2줄, `index.html`에 meta description/canonical 추가. `WebMvcConfig` 신규로 css/js/images에 `Cache-Control: public, max-age=600` 명시.
- 검증: 로컬 `bootRun`으로 `curl /robots.txt`, `curl /sitemap.xml`(정적 페이지 3개 정상 출력, 로컬 DB에 상품 0건이라 상품 URL은 없음 — 정상), `curl -I /css/components.css`(`Cache-Control: max-age=600, public` 확인), `curl -I /images/logo-icon.png`(동일), `curl -I /`(html은 원래대로 Cache-Control 헤더 없음, 회귀 없음) 전부 실측 확인.
- 흥미로운 발견: Security의 no-cache 헤더가 "모든 응답"이 아니라 정적 리소스(css/js/images) 응답에만 붙고 html에는 원래 안 붙어있었음 — 처음엔 "Security가 전체에 붙인다"고 짐작하고 주석을 썼다가, 프로덕션에서 `curl -I /`와 `curl -I /js/main.js`를 직접 비교해보고 짐작이 틀렸다는 걸 확인해서 주석을 정정함(짐작만으로 코드 주석을 남기지 않는 습관).
- 결과: `./gradlew test` 전체 재실행 — **BUILD SUCCESSFUL**, 회귀 없음.
