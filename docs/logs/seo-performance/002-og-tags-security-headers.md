# 002-og-tags-security-headers — Open Graph + 보안 헤더(HSTS) (로그)

## Attempt 1 — 2026-08-19  ✅ PASS

- 시도: `index.html`에 Open Graph/Twitter Card 메타태그 추가. 보안 헤더 실측(로컬+프로덕션, `curl -I` HEAD 요청으로 처음 확인했을 때 정적 페이지에 X-Content-Type-Options/X-Frame-Options가 빠진 것처럼 보였는데, 실제 GET 요청으로 재확인하니 전부 정상 존재 — HEAD 테스트 방법 자체의 착각이었음, 실제 결함 아니었음). 유일한 실제 갭인 HSTS 미적용을 발견 — `server.forward-headers-strategy: framework` 추가로 해결.
- 검증: 로컬 `bootRun`에서 `curl -H "X-Forwarded-Proto: https" ...`로 HTTPS 요청을 흉내내 `Strict-Transport-Security: max-age=31536000 ; includeSubDomains` 응답 확인, 일반 HTTP 요청에는 해당 헤더가 없음(정상)도 함께 확인해서 "HTTPS일 때만 HSTS가 붙는다"는 조건부 동작이 의도대로 됨을 검증.
- 결과: `./gradlew test` 전체 재실행 — **BUILD SUCCESSFUL**, 회귀 없음.
