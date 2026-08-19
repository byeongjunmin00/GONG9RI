# 001 — SEO 인덱싱 + 정적 자산 캐싱

대상: seo-performance (신규)
담당: 민병준

## 배경 / 요구

사용자가 "우리 사이트 인덱스 걸려있는건가? 속도 업 되어있는건가?"라고 질문. 확인해보니 둘 다 안 되어 있었음:
- `robots.txt`/`sitemap.xml`/meta description이 없어서 검색엔진이 이 사이트를 발견·크롤링할 진입점 자체가 없음.
- 프로덕션 응답 헤더를 직접 확인해보니 css/js/images가 `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`로 나가고 있어서 브라우저 캐싱이 전혀 안 되고 있었음(html은 원래도 이 헤더가 없어 문제없음).

사용자가 "둘 다 마저 손보자"로 승인.

## 설계

- 계약 변경: 신규 `GET /sitemap.xml`(공개), `GET /robots.txt`(정적 파일).
- 영향 계층: `service`(`SitemapService` 신규) → `controller`(`SitemapController` 신규), `config`(`WebMvcConfig` 신규, `SecurityConfig` permitAll 2줄 추가).
- 범위: sitemap은 정적 페이지 몇 개 + 전체 상품 상세만 포함(카테고리/검색 결과 페이지 등 파생 URL은 제외 — 정식 콘텐츠가 아니라서). 정적 자산 캐싱은 10분으로 짧게(자세한 이유는 `design.md` 참고 — 이 프로젝트는 배포가 잦고 cache busting 전략이 없어서 오래 캐싱하면 스테일 위험이 큼).

## 태스크

- [ ] `SitemapService.buildSitemapXml()` — 정적 페이지 3개 + 전체 상품
- [ ] `SitemapController` — `GET /sitemap.xml`
- [ ] `static/robots.txt` — `Sitemap:` 안내 포함
- [ ] `SecurityConfig`에 `/robots.txt`, `/sitemap.xml` permitAll 추가
- [ ] `index.html`에 meta description + canonical 추가
- [ ] `WebMvcConfig` — css/js/images에 `Cache-Control: public, max-age=600`

## 평가(통과) 기준

- `./gradlew test` 전체 통과(회귀 없음)
- 로컬에서 `curl /robots.txt`, `curl /sitemap.xml` 실제 응답 확인
- 로컬에서 css/js/images 응답 헤더가 `Cache-Control: max-age=600, public`으로 바뀌었는지, html은 그대로인지 확인
- 프로덕션 배포 후 동일하게 실측 확인
