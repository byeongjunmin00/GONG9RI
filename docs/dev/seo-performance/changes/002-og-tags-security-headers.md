# 002 — Open Graph 메타태그 + 보안 헤더(HSTS) 점검

대상: seo-performance
담당: 민병준

## 배경 / 요구

001(sitemap/robots.txt/캐싱)에 이어서, 사용자가 추천받은 두 개를 추가로 요청: 링크 공유 시 미리보기 카드(Open Graph), 보안 헤더 점검.

## 설계

- **Open Graph**: `index.html`에 `og:*`/`twitter:card` 메타태그 추가. 카카오톡 공유하기 버튼(`product.js`)은 이미 상품 상세 페이지별로 동적 카드를 만들어주고 있어서 겹치지 않음 — OG 태그는 "버튼 없이 링크만 붙여넣어도" 뜨는 범용 미리보기용. 모든 페이지가 정적 HTML이라 페이지별 동적 값을 못 넣어서 홈페이지 기준 값으로 고정(상품 상세 페이지별 OG는 서버사이드 렌더링이 필요해 스코프 밖).
- **보안 헤더 점검**: 실측 결과 대부분(X-Content-Type-Options/X-Frame-Options/X-XSS-Protection) 이미 Spring Security 기본값으로 정상 적용돼 있었음(처음엔 `curl -I`(HEAD 요청)로 확인해서 빠진 것처럼 보였는데, 실제 브라우저가 쓰는 GET으로 재확인하니 전부 있었음 — HEAD 요청 테스트 방법 자체의 착각이었지 실제 결함 아님). 유일하게 빠진 게 HSTS(항상 HTTPS로만 접속하게 강제) — Railway가 TLS를 프록시에서 종료하고 내부로 HTTP로 전달해서 Spring이 `request.isSecure()`를 false로 판단해 Security 기본 HSTS 작성기가 스킵하고 있었음. `server.forward-headers-strategy: framework`로 해결(Spring Boot가 `ForwardedHeaderFilter`를 자동 등록해 `X-Forwarded-Proto`를 신뢰).
- CSP(Content-Security-Policy)는 이번 스코프에서 제외 — 외부 CDN(Google Fonts, Pretendard, 카카오 JS SDK, PortOne 브라우저 SDK)을 전부 정확히 화이트리스트해야 해서 잘못 설정하면 그 기능들이 조용히 깨질 위험이 큼. 근거 없이 서둘러 넣기보다 스코프 밖으로 명시.

## 태스크

- [x] `index.html`에 `og:type`/`og:site_name`/`og:title`/`og:description`/`og:url`/`og:image`/`twitter:card` 추가
- [x] `application.yaml`에 `server.forward-headers-strategy: framework` 추가
- [x] 로컬에서 `X-Forwarded-Proto: https` 헤더를 흉내낸 요청으로 HSTS가 실제로 붙는지 실측(일반 HTTP 요청엔 안 붙는 것도 함께 확인)

## 평가(통과) 기준

- `./gradlew test` 전체 통과
- 로컬 실측: 일반 HTTP 요청엔 HSTS 없음, `X-Forwarded-Proto: https` 흉내낸 요청엔 `Strict-Transport-Security: max-age=31536000 ; includeSubDomains` 확인
- 프로덕션 배포 후 실제 HTTPS 요청에 HSTS 헤더가 붙는지 재확인
- 남은 것(스코프 밖, 정직하게 기록): CSP 미도입, 상품 상세 페이지별 동적 OG 카드 미지원(정적 페이지 구조의 한계)
