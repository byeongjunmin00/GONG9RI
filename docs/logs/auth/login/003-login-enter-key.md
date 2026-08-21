# 003-login-enter-key — 로그인 엔터 키 지원 (로그)

## Attempt 1 — 2026-08-21  ✅ PASS

- 시도:
  - `src/main/resources/static/js/login.js` 및 `src/main/resources/static/js/admin-login.js`에 `usernameInput` Enter 키 keydown 이벤트 핸들러 추가
  - `username` 필드에서 Enter 입력 시: 비밀번호가 비어있으면 `passwordInput.focus()`로 포커스 이동, 입력되어 있으면 form submit
  - `password` 필드에서 Enter 입력 시: 브라우저 기본 form submit으로 로그인 요청 전송
  - `event.isComposing` 상태 체크로 한글 IME 조합 중 중복 이벤트 방지
- 결과:
  - `./gradlew test --tests "*Auth*" --tests "*Login*"` 전체 통과 (BUILD SUCCESSFUL)
- 증거:
  - `login.js` 및 `admin-login.js` 엔터 키 인터랙션 이벤트 핸들러 정상 반영

## Attempt 2 — 2026-08-21  ✅ PASS

- 시도:
  - 아이디 필드에서 엔터로 비밀번호 필드로 포커스 이동한 후 비밀번호 필드에서 엔터를 칠 때 브라우저 묵시적 제출이 동작하지 않는 현상 수정
  - `submitForm()` 함수를 명시적으로 분리하고, `passwordInput` 및 `usernameInput`(비밀번호 채워진 경우)의 `keydown` 이벤트에서 Enter 입력 시 `event.preventDefault()` 및 `submitForm()` 직접 호출
  - `admin-login.js`에도 동일하게 적용
- 결과:
  - 포커스 이동 후에도 마우스 클릭 없이 즉시 엔터 키로 로그인 폼 제출 실행 가능
- 증거(Evaluate, 2026-08-21):
  - 코드 리뷰: `submitForm()` 분리로 아이디 Enter(비번 입력됨)/비번 Enter/버튼 클릭 3개 트리거가 모두 같은
    함수를 호출 — 중복 제출 없음, `event.isComposing` 가드 유지 확인.
  - 자동화 브라우저 도구(synthetic KeyboardEvent)로는 이 페이지의 실제 `#password` 필드에서만
    `keydown` 리스너가 트리거되지 않는 현상이 있었음(같은 방식으로 만든 임시 `<input type=password]`는
    정상 동작) — 원인 특정은 못 했으나 크롬이 인증된 비밀번호 필드에 대해 synthetic 이벤트를 다르게
    처리하는 것으로 추정. 마우스 클릭 제출은 자동화 도구로 정상 확인함(회귀 없음).
  - **실제 사용자가 로컬 브라우저에서 아이디+비밀번호 입력 후 엔터 키로 직접 로그인 성공 확인**(2026-08-21,
    호출자 직접 테스트) — 자동화 도구의 한계를 실제 수동 확인으로 보완.

