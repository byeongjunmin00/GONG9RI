# 002-kakao-force-reauth — 카카오 로그아웃 후 재로그인 시 강제 재인증 (로그)

## Attempt 1 — 2026-08-14  ✅ PASS
- 시도: `AuthController.kakaoLogin()`이 조립하는 카카오 인가 요청 URL에 `&prompt=login` 파라미터 추가.
  카카오 공식 문서(Kakao Developers REST API) 확인 결과, `prompt=login`은 "기존 사용자 인증
  여부와 상관없이 카카오계정 로그인 화면을 출력"하는 파라미터 — 우리 앱에서 로그아웃해도 카카오
  자체 세션(기본 24시간, "로그인 상태 유지" 시 최대 1개월)이 남아있으면 재인증 없이 통과되던
  문제를 해결. 카카오톡 인앱 브라우저에서는 미지원(공식 문서 명시).
- 근거: 사용자가 실제 프로덕션(`gong9ri-production.up.railway.app`)에서 직접 재현 확인 —
  로그아웃 → "카카오로 로그인" 클릭 → 화면 전환 없이 즉시 재로그인됨. 재현 당시 해당 브라우저에
  카카오 자체 로그인 세션이 남아있었던 것으로 확인(별도 탭에 `accounts.kakao.com` 실제 로그인
  폼이 대기 중이었음).
- 결과: `KakaoLoginTest`에 신규 테스트 `kakaoLogin_authorizeUrl_includesPromptLogin` 추가 —
  `GET /api/auth/kakao/login`의 `Location` 헤더에 `prompt=login` 포함 확인.
  `./gradlew test --rerun-tasks` 전체 통과(`tests=203, failures=0, errors=0` — 기존
  202개에서 1개 순증).
- 증거(API 샘플): `GET /api/auth/kakao/login` → `302`,
  `Location: https://kauth.kakao.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code&prompt=login&state=...`
